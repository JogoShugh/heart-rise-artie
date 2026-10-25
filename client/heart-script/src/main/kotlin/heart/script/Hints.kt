package heart.script

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import kotlin.time.Duration

/*
 * Hints (spec/PLAN-LISTENERS.md, slice 2). A listener looks again only when something its last look read may have
 * changed. The watch polls each resource some listener read, one poll however many listeners read it, at the
 * quickest pace any of them asks, and hints each listener whose last read there no longer shows what it shows now.
 * A hint is never the truth: the listener reads the resource again itself, and acts only on what it offers then.
 */

/** What a representation shows, in one string: two reads that show the same thing are equal. */
internal fun Representation.shows(): String =
    "$state $offered $links ${embedded.mapValues { (_, all) -> all.map { it.title to it.state } }}"

/**
 * A listener's hints, by address. However many came for one address, one read again is enough. It waits on the
 * run's [time], and no longer once [over].
 */
internal class Inbox(private val time: Time, private val over: () -> Boolean) {
    private val hinted = ConcurrentLinkedQueue<String>()

    fun put(address: String) {
        hinted += address
        time.poke()
    }

    /** The addresses hinted since the last take, waiting at most [wait] for the first; empty when none came. */
    fun take(wait: Duration): Set<String> {
        time.await(wait) { hinted.isNotEmpty() || over() }
        return generateSequence { hinted.poll() }.toSet()
    }
}

/** The watch's polls: one for each resource a listener's last look read, shared by every listener that read it. */
internal class Polls(private val run: Run, private val watch: Watch, private val threads: ExecutorService) {
    /** One resource polled: what each listener last saw there, and how often it is read. */
    private class Poll(val address: String, @Volatile var every: Duration) {
        val seen = ConcurrentHashMap<Inbox, String>()
    }

    private val open = ConcurrentHashMap<String, Poll>()

    /**
     * The listener with [inbox] last read [read], each address with what it showed there: it is hinted when one of
     * them shows something else, and no longer for what it didn't read. Each is read at least every [every].
     */
    fun want(inbox: Inbox, every: Duration, read: Map<String, String>) {
        open.values.filter { it.address !in read }.forEach { it.seen.remove(inbox) }
        read.forEach { (address, shows) ->
            val poll = open.computeIfAbsent(address) { at -> Poll(at, every).also { start(it) } }
            if (every < poll.every) poll.every = every
            poll.seen[inbox] = shows
        }
    }

    /** [poll] begun on a thread of its own; none once the watch is over. */
    private fun start(poll: Poll) {
        if (watch.on(run)) {
            try {
                threads.submit(run.time.thread { polling(poll) })
            } catch (over: RejectedExecutionException) {
                // The watch ended as the poll was asked for: nothing is polled after it.
                run.time.poke().also { over.message }
            }
        }
    }

    /** One resource read again and again, on a thread and with a client of its own, while the watch is on. */
    private fun polling(poll: Poll) {
        val navigator = run.navigator()
        while (watch.on(run)) {
            run.time.await(poll.every) { !watch.on(run) }
            if (watch.on(run) && poll.seen.isNotEmpty()) read(navigator, poll.address)?.let { hint(poll, it) }
        }
    }

    /** Each listener whose last read of [poll]'s resource doesn't show what it [shows] now is hinted, once. */
    private fun hint(poll: Poll, shows: String) = poll.seen.forEach { (inbox, seen) ->
        if (seen != shows) {
            poll.seen[inbox] = shows
            inbox.put(poll.address)
        }
    }

    /** What [address] shows now; null when it could not be read this time (a refusal, an error): polled again. */
    private fun read(navigator: Navigator, address: String): String? =
        runCatching { (navigator.visit(address) as? Answer.Done)?.now?.shows() }.getOrNull()
}
