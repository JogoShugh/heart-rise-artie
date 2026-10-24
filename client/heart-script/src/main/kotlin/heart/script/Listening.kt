package heart.script

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/*
 * A watch with listeners (spec/PLAN-LISTENERS.md, slice 1): each listener looks again and again on a thread of its
 * own, a virtual thread, with a client and a record of its own, so a slow server or a slow handler holds up only the
 * listener it belongs to. The watch's condition is read on the watch's own thread, at its own pace.
 */

/** The listeners of a watch, as its block names them. */
@HeartDsl
class Listeners internal constructor() {
    internal val named = mutableListOf<Listener>()

    /** A listener, [name]d: [look] at where the watch stands, read again, every [every], on a thread of its own. */
    fun listen(name: String, every: Duration = 15.seconds, look: Here.() -> Unit) {
        named += Listener(name, every, look)
    }
}

internal class Listener(val name: String, val every: Duration, val look: Here.() -> Unit)

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
    val forks = listeners.map { run.fork() }
    val running = Executors.newVirtualThreadPerTaskExecutor().use { threads ->
        val running = listeners.zip(forks).map { (listener, fork) ->
            threads.submit { fork.listening(listener, watch) }
        }
        try {
            looking(watch, every, until) { running.any { it.isDone } }
        } finally {
            watch.over.set(true)
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

/** One listener's loop, on its own thread, with a client of its own: [listener]'s look, a pause apart. */
private fun Run.listening(listener: Listener, watch: Watch) {
    val navigator = TracingNavigator(navigator(), this)
    val here = Here(navigator, this, arrived(0, watch.start, navigator.enter(watch.start)))
    watching.active = true
    while (watch.on(this)) {
        here.attempt(watch.start, "${listener.name}: a look failed") { listener.look(this) }
        if (watch.on(this)) pause(listener.every)
    }
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
    fork.watching.finished += watching.finished
    fork.watching.left += watching.left
}

/** What [fork] did and noted, after this run's own. */
internal fun Run.join(fork: Run) = synchronized(this) {
    trace += fork.trace
    notes += fork.notes
}
