package heart.script

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * How a watch's threads wait (spec/PLAN-LISTENERS.md, slices 2 and 3): for something to hold, a hint or the end of
 * the watch, or for a while to pass. On the real clock, or on a test's virtual time.
 */
interface Time {
    /** Wait until [until] holds, or [atMost] has passed; whether it holds. */
    fun await(atMost: Duration, until: () -> Boolean): Boolean

    /** What a waiter waits on may hold now: each waiter looks again. */
    fun poke()

    /**
     * [block] as one of the watch's own threads runs it, counted as busy from now, before the thread starts, until
     * it ends, but while it waits.
     */
    fun <T> thread(block: () -> T): () -> T = block
}

/** The real clock: a wait takes the time it says. */
class RealTime : Time {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    override fun await(atMost: Duration, until: () -> Boolean): Boolean = lock.withLock {
        var left = atMost.inWholeNanoseconds
        while (!until() && left > 0) left = changed.awaitNanos(left)
        until()
    }

    override fun poke() = lock.withLock { changed.signalAll() }
}

/**
 * A test's virtual time: it stands still while any of the watch's threads is busy, and moves, to the earliest time
 * a waiter waits until, only once every one of them waits. A 10-minute give-up takes no real time, and no thread
 * finds more time passed than the others let pass. A thread busy for longer than [grace], held by a slow server
 * on purpose, counts as waiting, so the others go on meanwhile, as they would on the real clock.
 */
class VirtualTime(start: java.time.Instant, private val grace: Duration = 1.seconds) : Time {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var now = start

    /** The watch's threads that are running, each since when, on the real clock. */
    private val busy = mutableMapOf<Thread, Long>()

    /** Every thread that waits, each until when. */
    private val waiting = mutableMapOf<Thread, java.time.Instant>()

    fun now(): java.time.Instant = lock.withLock { now }

    /** Wait for [d] to pass: a run's pause. */
    fun pause(d: Duration) {
        await(d) { false }
    }

    /** Threads made but not yet begun: time stands still for them too. */
    private var starting = 0

    override fun <T> thread(block: () -> T): () -> T {
        lock.withLock { starting++ }
        return {
            val me = Thread.currentThread()
            lock.withLock {
                starting--
                busy[me] = System.nanoTime()
            }
            try {
                block()
            } finally {
                lock.withLock {
                    busy.remove(me)
                    changed.signalAll()
                }
            }
        }
    }

    override fun await(atMost: Duration, until: () -> Boolean): Boolean = lock.withLock {
        val me = Thread.currentThread()
        val ends = now.plus(atMost.toJavaDuration())
        val was = busy.remove(me)
        waiting[me] = ends
        changed.signalAll()
        try {
            while (!until() && now.isBefore(ends)) {
                if (idle()) move()
                if (!until() && now.isBefore(ends)) changed.await(grace.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            }
            until()
        } finally {
            waiting.remove(me)
            if (was != null) busy[me] = System.nanoTime()
        }
    }

    override fun poke() = lock.withLock { changed.signalAll() }

    /** Whether no thread of the watch is busy, but for one held longer than [grace]. */
    private fun idle(): Boolean {
        val held = System.nanoTime() - grace.inWholeNanoseconds
        return starting == 0 && busy.values.all { it < held }
    }

    /** Time moved on to the earliest time a waiter waits until, and every waiter told. */
    private fun move() {
        val next = waiting.values.minOrNull() ?: return
        if (next.isAfter(now)) now = next
        changed.signalAll()
    }
}
