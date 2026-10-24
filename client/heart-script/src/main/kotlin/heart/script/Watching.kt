package heart.script

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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

    /** Each member a `whenever` visited, by address: its state as last read. */
    val seen = mutableMapOf<String, String>()

    /** Each wait not over yet, by its resource's address and act: since when it waits. */
    val waiting = mutableMapOf<String, java.time.Instant>()

    /** Each wait that is over, by its resource's address and act: its act done, or its otherwise. */
    val finished = mutableSetOf<String>()

    /** Each resource a goal's If left alone, by its address: said so once. */
    val left = mutableSetOf<String>()

    /** Whether a `once` waits on the resource at [address]. */
    fun waitsOn(address: String): Boolean = waiting.keys.any { it.startsWith("$address ") }

    /** What a watch run before with [file] kept: its finished waits and the resources it left. */
    fun load(file: java.nio.file.Path) {
        if (!java.nio.file.Files.exists(file)) return
        var into: MutableSet<String>? = null
        java.nio.file.Files.readAllLines(file).forEach { line ->
            when {
                line.startsWith("finished:") -> into = finished
                line.startsWith("left:") -> into = left
                line.startsWith("- ") ->
                    into?.add(Json.parseToJsonElement(line.removePrefix("- ")).jsonPrimitive.content)
            }
        }
    }

    /** What this watch did, kept in [file] for a run after it: YAML, a list each, each item a quoted string. */
    fun save(file: java.nio.file.Path) {
        fun listed(name: String, items: Set<String>) = if (items.isEmpty()) {
            "$name: []\n"
        } else {
            "$name:\n" + items.joinToString("") { "- ${JsonPrimitive(it)}\n" }
        }
        java.nio.file.Files.writeString(file, listed("finished", finished) + listed("left", left))
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
    until: Here.() -> Boolean,
    look: Here.() -> Unit,
) {
    val start = held.address
    val ends = run.clock().plus(atMost.toJavaDuration())
    run.remember?.let { run.watching.load(it) }
    run.watching.active = true
    try {
        var over = false
        var looks = 0
        while (!over && run.clock().isBefore(ends)) {
            looks++
            attempt(start, "A look failed") { look() }
            run.remember?.let { run.watching.save(it) }
            attempt(start, "Reading whether the watch is over failed") { over = until() }
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
 * the last look, and those a `once` still waits on.
 */
fun Here.whenever(rel: String = "item", block: Here.() -> Unit) {
    val watching = run.watching
    members(rel) {
        val address = held.address
        val now = held.state.toString()
        val changed = watching.seen.put(address, now) != now
        if (!watching.active || changed || watching.waitsOn(address)) block()
    }
}

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
    val since = watching.waiting.getOrPut(key) { run.clock() }
    var waiting = true
    while (waiting) {
        val waited = java.time.Duration.between(since, run.clock()).toKotlinDuration()
        val over = offers(rel) || waited >= giveUp
        if (over) {
            watching.waiting.remove(key)
            watching.finished += key
            if (offers(rel)) block() else otherwise(why(rel))
        }
        waiting = !over && !watching.active
        if (waiting) {
            run.pause(every)
            sync()
        }
    }
}
