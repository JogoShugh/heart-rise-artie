package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

/*
 * A bare watch (spec/ideas/bare-runs.md, "Rails"): the goal words Until, Whenever, If, Once, Or give up and
 * Otherwise over plain calls to fixed addresses, as a hand-written script polls. What a look remembers is the same
 * as a kept script's watch (Watching): what each member was, what waits, what is over, what was left. Nothing is
 * offered or refused by the client: where a kept script waits for a form, a bare `once` tries its call, and the
 * server's refusal is the only gate.
 */

/**
 * Look again and again, [every] apart: run [look], then [until]; stop once [until] holds, or after [atMost]. A look
 * that fails (a server's error, a network that failed) is noted, and the next look tries again.
 */
fun Bare.watch(
    every: Duration = 15.seconds,
    atMost: Duration = 2.hours,
    until: Bare.() -> Boolean,
    look: Bare.() -> Unit,
) {
    val ends = run.clock().plus(atMost.toJavaDuration())
    run.remember?.let { run.watching.keep(it) }
    run.watching.active = true
    try {
        var over = false
        var looks = 0
        while (!over && run.clock().isBefore(ends)) {
            looks++
            tried("A look failed") { look() }
            tried("Reading whether the watch is over failed") { over = until() }
            if (!over) run.pause(every)
        }
        if (!over) note("The watch stopped after $atMost, $looks looks, its condition not met.")
    } finally {
        run.watching.active = false
    }
}

/**
 * Visit each member of [list], as [block]; in a watch, only those new or changed since the last look, and those a
 * `once` still waits on. A member is known by its `url`, else its `id`, else all it holds. Its guards (`has`,
 * `leaveWhere`) end its turn. A member is seen as listed: what the turn did to it shows at the next look as a
 * change, and its turn finds its `once` over already.
 */
fun Bare.whenever(list: JsonElement?, block: Bare.(member: JsonElement) -> Unit) {
    val watching = run.watching
    val members = list as? JsonArray ?: error("not a list: $list")
    for (member in members) {
        val key = key(member)
        val now = member.toString()
        val due = !watching.active || watching.differs(key, now) || watching.waitsOn(key)
        if (!due) continue
        val outer = this.member
        this.member = key
        try {
            block(member)
        } catch (_: MemberLeft) {
            // A guard ended the member's turn.
        } finally {
            this.member = outer
            if (watching.active) watching.saw(key, now)
        }
    }
}

/**
 * Send [method] to [url] with [body] until the server takes it, one time for this member. Until then, wait: in a
 * watch, the next try is at the next look; outside one, a second apart. A wait longer than [giveUp] ends with
 * [otherwise], told why the server refused, in its own message (null when it says nothing).
 */
fun Bare.once(
    method: String,
    url: String,
    body: Any? = null,
    giveUp: Duration = 10.minutes,
    otherwise: Bare.(why: String?) -> Unit = {},
) {
    val watching = run.watching
    val key = "${member ?: url} $method $url"
    val since = watching.waiting[key] ?: run.clock()
    var waiting = key !in watching.finished
    while (waiting) {
        val answer = sent(method, url, body)
        val taken = answer.status in Bare.OK
        val waited = java.time.Duration.between(since, run.clock()).toKotlinDuration()
        if (taken || waited >= giveUp) {
            watching.over(key)
            if (!taken) otherwise(why(answer))
            waiting = false
        } else {
            watching.waits(key, since)
            watching.ends[key] = since.plus(giveUp.toJavaDuration())
            waiting = !watching.active
            if (waiting) run.pause(1.seconds)
        }
    }
}

/** Run [block]; a stop in it, a server's error or a network that failed is noted as [failed], and the watch goes on. */
private fun Bare.tried(failed: String, block: () -> Unit) {
    val said = try {
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

/** Why the server refused, in its own words: its answer's `message`, else the answer as it came. */
private fun Bare.why(answer: Received): String? {
    val body = answer.body?.takeIf { it.isNotBlank() } ?: return null
    val message = runCatching { parsed(answer)?.get("message") as? JsonPrimitive }.getOrNull()
    return message?.content ?: body
}

/** How a member of a list is known from look to look. */
private fun key(member: JsonElement): String {
    val url = member["url"] as? JsonPrimitive
    val id = member["id"] as? JsonPrimitive
    return url?.content ?: id?.content ?: member.toString()
}
