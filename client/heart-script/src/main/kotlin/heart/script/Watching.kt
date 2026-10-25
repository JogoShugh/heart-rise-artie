package heart.script

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/*
 * A script that keeps watching (spec/ideas/goals-that-work-alongside-others.md, "Watching for a long time"): the
 * goal words Until, Whenever, Once, Or give up and Otherwise, as heart-script. One loop, no threads: each look
 * visits what changed, and a wait not over yet is left for the next look, so one resource never holds up another.
 */

/** What a run's watch remembers from look to look. */
internal class Watching {
    /** Whether a watch is running: a `once` in it leaves a wait for the next look rather than waiting. */
    var active = false

    /**
     * Each member a `whenever` visited, by address: a digest of its state as last read, which is also the id of the
     * hint it answered: a state answered once is not answered again, in this run or, by the journal, a later one.
     */
    val seen = ConcurrentHashMap<String, String>()

    /** Each wait not over yet, by its resource's address and act: since when it waits. */
    val waiting = ConcurrentHashMap<String, Instant>()

    /** Each wait not over yet, by its resource's address and act: when it gives up (spec/PLAN-LISTENERS.md, 3). */
    val ends = ConcurrentHashMap<String, Instant>()

    /** Each wait that is over, by its resource's address and act: its act done, or its otherwise. */
    val finished: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Each resource a goal's If left alone, by its address: said so once. */
    val left: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Where what this watch does is kept as it goes (spec/PLAN-LISTENERS.md, slice 5); null: nowhere. */
    var journal: Journal? = null

    /** Whether a `once` waits on the resource at [address]. */
    fun waitsOn(address: String): Boolean = waiting.keys.any { it.startsWith("$address ") }

    /** Whether the member at [address], read as [state], shows something else than when last seen. */
    fun differs(address: String, state: String): Boolean = seen[address] != Journal.digest(state)

    /** The member at [address] seen as [state]. */
    fun saw(address: String, state: String) {
        val digest = Journal.digest(state)
        if (seen.put(address, digest) != digest) journal?.add("seen", "address" to address, "state" to digest)
    }

    /** Since when the wait [key] waits: since [now], if it is new. */
    fun waits(key: String, now: Instant): Instant = waiting[key] ?: now.also {
        waiting[key] = it
        journal?.add("wait", "key" to key, "since" to it.toString())
    }

    /** The wait [key] is over: its act done, or its otherwise. */
    fun over(key: String) {
        waiting.remove(key)
        ends.remove(key)
        finished += key
        journal?.add("over", "key" to key)
    }

    /** The resource at [address] left alone by an If: whether it is the first time. */
    fun leave(address: String): Boolean = left.add(address).also { first ->
        if (first) journal?.add("left", "address" to address)
    }

    /** What a watch run before kept in [file], and this one's kept there too, from now on. */
    fun keep(file: java.nio.file.Path) {
        if (journal?.file == file) return
        journal = Journal(file).also { it.replay(this) }
    }
}

/**
 * Look again and again, [every] apart, at the held resource: read it again, run [look] there, then [until] there;
 * stop once [until] holds, or after [atMost]. A look that fails (a server's error, a refusal) is noted, and the next
 * look tries again. Ends where it began, read again.
 */
fun Here.watch(
    every: Duration = 15.seconds,
    atMost: Duration = 2.hours,
    until: Facts.() -> Unit,
    look: Here.() -> Unit,
) {
    val start = held.address
    val ends = run.clock().plus(atMost.toJavaDuration())
    run.remember?.let { run.watching.keep(it) }
    run.watching.active = true
    try {
        var over = false
        var looks = 0
        while (!over && run.clock().isBefore(ends)) {
            looks++
            attempt(start, "A look failed") { look() }
            attempt(start, "Reading whether the watch is over failed") { over = holds(until) }
            if (!over) run.pause(every)
        }
        if (!over) note("The watch stopped after $atMost, $looks looks, its condition not met.")
    } finally {
        run.watching.active = false
    }
}

/**
 * Read [start] again and run [block] there; a stop in it, a resource that could not be read (a server's error) or a
 * network that failed, is noted as [failed], and the watch goes on.
 */
internal fun Here.attempt(start: String, failed: String, block: Here.() -> Unit) {
    val said = try {
        held = arrived(++run.step, "\"watch\"", navigator.recall(start))
        block()
        null
    } catch (ended: GoalStopped) {
        throw ended
    } catch (stopped: ScriptStopped) {
        stopped.reason
    } catch (unread: IllegalStateException) {
        unread.message
    } catch (network: java.io.IOException) {
        network.message ?: network.javaClass.simpleName
    }
    said?.let { note("$failed: $it; looking again next time.") }
}

/**
 * Visit each member the held list links under [rel], as `members` does; in a watch, only those new or changed since
 * the last look, and those a `once` still waits on. Which members are answered is said in [block] itself, by its
 * guards (`has`, `leaveWhere`, `madeBy`, `saying`): one that fails ends that member's turn
 * (spec/ideas/dsl-conditions-as-facts.md).
 */
fun Here.whenever(rel: String = "item", block: Here.() -> Unit) {
    val watching = run.watching
    members(rel) {
        val address = held.address
        val now = held.state.toString()
        val due = !watching.active || watching.differs(address, now) || watching.waitsOn(address)
        if (due) {
            val began = run.step
            var changed = true
            try {
                block()
            } catch (_: MemberLeft) {
                // A guard ended the turn; where nothing was read or done before it, the member is as it was.
                changed = run.step != began
            } finally {
                // What the block did to the member is the watch's own change, not one to answer at the next look,
                // whether its steps all went through or one failed after another had acted. It is seen once it is
                // answered, so a run stopped while answering it answers it again.
                if (watching.active) watching.saw(address, if (changed) reread(address) else now)
            }
        } else if (watching.active) {
            watching.saw(address, now)
        }
    }
}

/** The member at [address], read again after its turn, as the watch will compare it next time. */
private fun Here.reread(address: String): String =
    arrived(run.step, "\"watch\"", navigator.recall(address)).state.toString()

/**
 * Once the held resource offers [rel], run [block], one time for this resource. Until then, wait: in a watch, the
 * wait is left for the next look; outside one, the resource is read again, [every] apart. A wait longer than
 * [giveUp] ends with [otherwise], told why [rel] isn't offered, in the server's words (null when it says nothing).
 */
fun Here.once(
    rel: String,
    giveUp: Duration = 10.minutes,
    every: Duration = 1.seconds,
    otherwise: Here.(why: String?) -> Unit = {},
    block: Here.() -> Unit,
) {
    val watching = run.watching
    val key = "${held.address} $rel"
    if (key in watching.finished) return
    val since = watching.waiting[key] ?: run.clock()
    var waiting = true
    while (waiting) {
        val waited = java.time.Duration.between(since, run.clock()).toKotlinDuration()
        val over = offers(rel) || waited >= giveUp
        if (over) {
            watching.over(key)
            if (offers(rel)) block() else otherwise(why(rel))
        } else {
            // A wait begins only when it isn't over at once: kept, with when it gives up.
            watching.waits(key, since)
            watching.ends[key] = since.plus(giveUp.toJavaDuration())
        }
        waiting = !over && !watching.active
        if (waiting) {
            run.pause(every)
            sync()
        }
    }
}
