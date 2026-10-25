package heart.script

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/*
 * A watch with listeners (spec/PLAN-LISTENERS.md, slice 1): each listener looks again and again on a thread of its
 * own, a virtual thread, with a client and a record of its own, so a slow server or a slow handler holds up only the
 * listener it belongs to. The watch's condition is read on the watch's own thread, at its own pace.
 */

/** The listeners of a watch, as its block names them. */
@HeartDsl
class Listeners internal constructor() {
    internal val named = mutableListOf<Listener>()

    /**
     * A listener, [name]d: [look] at where the watch stands, or at the address [from], read again, every [every], on
     * a thread of its own.
     */
    fun listen(name: String, every: Duration = 15.seconds, from: String? = null, look: Here.() -> Unit) {
        named += Listener(name, every, from, look)
    }
}

internal class Listener(val name: String, val every: Duration, val from: String?, val look: Here.() -> Unit)

/**
 * Watch with the listeners [block] names, each on its own, until [until] holds where the watch stands, read every
 * [every] on this thread, or [atMost] has passed. A listener's failed look is noted, and it looks again; a goal's stop
 * in a listener ends them all, and the watch. What each listener did and noted joins this run's record at the end.
 */
fun Here.listeners(
    every: Duration = 15.seconds,
    atMost: Duration = 2.hours,
    until: Here.() -> Boolean,
    block: Listeners.() -> Unit,
) {
    val listeners = Listeners().apply(block).named
    val start = held.address
    val watch = Watch(start, run.clock().plus(atMost.toJavaDuration()), AtomicBoolean(false))
    run.remember?.let { run.watching.keep(it) }
    val forks = listeners.map { run.fork() }
    val polls = Polls(run, watch)
    val running = Executors.newVirtualThreadPerTaskExecutor().use { threads ->
        val running = listeners.zip(forks).map { (listener, fork) ->
            threads.submit(run.time.thread { fork.listening(listener, watch, polls) })
        }
        try {
            // The watch's own thread is one of its threads too: time doesn't move while it reads.
            run.time.thread { looking(watch, every, until) { running.any { it.isDone } } }()
        } finally {
            watch.over.set(true)
            run.time.poke()
            polls.close()
        }
        running
    }
    forks.forEach { run.join(it) }
    running.forEach { listener -> listener.stopped()?.let { throw it } }
}

/** Where a watch with listeners stands, when it ends at the latest, and whether it is over. */
internal class Watch(val start: String, val ends: java.time.Instant, val over: AtomicBoolean) {
    fun on(run: Run) = !over.get() && run.clock().isBefore(ends)
}

/** The watch's own loop: [until] read every [every], until it holds, or a listener [ended] early, or time is up. */
private fun Here.looking(watch: Watch, every: Duration, until: Here.() -> Boolean, ended: () -> Boolean) {
    var done = false
    while (!done && watch.on(run)) {
        attempt(watch.start, "Reading whether the watch is over failed") { done = until() }
        done = done || ended()
        if (!done) run.pause(every)
    }
}

/**
 * One listener's loop, on its own thread, with a client of its own: [listener]'s look, then again each time a
 * resource it read is hinted changed (Hints.kt), and when a `once` in it gives up: a timeout, not a look's chance.
 */
private fun Run.listening(listener: Listener, watch: Watch, polls: Polls) {
    val start = listener.from ?: watch.start
    val navigator = TracingNavigator(navigator(), this)
    val here = Here(navigator, this, arrived(0, start, navigator.enter(start)))
    val inbox = Inbox(time) { !watch.on(this) }
    watching.active = true
    var look = true
    while (watch.on(this)) {
        if (look) {
            val from = trace.size
            here.attempt(start, "${listener.name}: a look failed") { listener.look(this) }
            polls.want(inbox, listener.every, read(from))
        }
        val due = watching.ends.values.minOrNull()
        val left = due?.let { java.time.Duration.between(clock(), it).toKotlinDuration() }
        look = inbox.take(left?.coerceIn(Duration.ZERO, listener.every) ?: listener.every).isNotEmpty() ||
            (due != null && !clock().isBefore(due))
    }
}

/**
 * What the look that made this run's steps from [from] on saw of each resource, by address: what it first read
 * there, which is what it answered, or what its own last write there answered. A read again later in the look, at a
 * block's end, may already show a change it never answered, and must not hide it from the next hint.
 */
private fun Run.read(from: Int): Map<String, String> {
    val seen = mutableMapOf<String, String>()
    trace.drop(from).forEach { step ->
        val now = step.now ?: return@forEach
        val wrote = step.used?.method?.let { !it.equals("GET", ignoreCase = true) } == true
        if (wrote || now.address !in seen) seen[now.address] = now.shows()
    }
    return seen
}

/** What stopped a listener's thread, if anything did. */
private fun Future<*>.stopped(): Throwable? = try {
    get()
    null
} catch (failed: ExecutionException) {
    failed.cause
}

/**
 * A run for one listener: the same inputs, clock, pause and client maker, and a record of its own, so steps made on
 * another thread at the same time never mix with this run's; [join] adds it after.
 */
internal fun Run.fork(): Run = Run(inputs, navigator = navigator, pause = pause).also { fork ->
    fork.clock = clock
    fork.time = time
    fork.entry = entry
    fork.reasoning = reasoning
    fork.onStep = onStep
    fork.onNote = onNote
    fork.source = source
    fork.declared += declared
    fork.values += values
    fork.inputsUsed += inputsUsed
    // What the watch saw and did before its listeners began, so none of them does it again.
    fork.watching.seen += watching.seen
    fork.watching.waiting += watching.waiting
    fork.watching.ends += watching.ends
    fork.watching.finished += watching.finished
    fork.watching.left += watching.left
    // One journal for the whole watch: every listener adds to it.
    fork.watching.journal = watching.journal
}

/** What [fork] did and noted, after this run's own. */
internal fun Run.join(fork: Run) = synchronized(this) {
    trace += fork.trace
    notes += fork.notes
}
