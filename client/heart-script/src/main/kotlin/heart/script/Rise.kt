package heart.script

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A reply a host keeps for a run (RISE): its [address], and where the [keys] the host signs with are. */
class Reply(val address: String, val keys: String)

/**
 * The replies a host keeps for the runs it hosts (rise.feature). ARTIE never serves: the host does, and ARTIE asks
 * it for a reply before each request it wants answered later, and waits on the host to say a reply changed.
 */
interface Replies {
    /** A new reply, awaiting its answer. */
    fun open(): Reply

    /** The request made with [reply] brought [request] into being: the party there answers the reply. */
    fun asked(reply: Reply, request: String)

    /** Waits until [reply] changes, or [atMost] passes: whether it changed. */
    fun await(reply: Reply, atMost: Duration): Boolean
}

/**
 * Select the form [rel] with [values], asking to be answered later on a reply kept as [reply]: the host opens it,
 * and the act carries it in its envelope (`hr:reply-to`, and `hr:keys`, where the host's keys are). What the act
 * brought into being is held after it, and the host is told it answers the reply.
 */
fun Here.ask(rel: String, reply: String, values: Map<String, Any?> = emptyMap()) {
    val replies = run.replies
        ?: throw ScriptStopped(run.step + 1, "\"$rel\"", "\"this run keeps no replies: a host gives it them\"")
    val opened = replies.open()
    val envelope = mapOf(
        "hr:reply-to" to mapOf("href" to opened.address),
        "hr:keys" to mapOf("href" to opened.keys),
    )
    act(rel, fields = {
        values.forEach { (name, value) -> name to value }
        "_links" to envelope
    })
    replies.asked(opened, held.address)
    run.replied[reply] = opened
}

/**
 * Once the reply kept as [reply] is answered, read it, keep it by that name, and run [block] holding it when it is
 * completed, or [failed] when it failed; then back where the script was. Until then, wait on the host to say it
 * changed. With no answer in [giveUp], [otherwise] runs here, told why.
 */
fun Here.onceAnswered(
    reply: String,
    giveUp: Duration = 30.minutes,
    failed: (Here.() -> Unit)? = null,
    otherwise: (Here.(String) -> Unit)? = null,
    block: Here.() -> Unit,
) {
    val opened = run.replied[reply]
        ?: throw ScriptStopped(run.step + 1, "\"once-answered\"", "\"no reply is kept as $reply\"")
    val replies = checkNotNull(run.replies)
    val end = run.clock() + giveUp.toJavaDuration()
    var answered = false
    at(opened.address) {
        while (state["state"] == AWAITING) {
            val left = java.time.Duration.between(run.clock(), end)
            if (left.isNegative || left.isZero) return@at
            if (replies.await(opened, left.toKotlinDuration())) sync()
        }
        answered = true
        keepAs(reply)
        if (state["state"] == FAILED) failed?.invoke(this) else block()
    }
    if (!answered) otherwise?.invoke(this, "no answer in $giveUp")
}

private fun Duration.toJavaDuration(): java.time.Duration = java.time.Duration.ofMillis(inWholeMilliseconds)

private fun java.time.Duration.toKotlinDuration(): Duration = kotlin.time.Duration.parseIsoString(toString())

private const val AWAITING = "awaiting"
private const val FAILED = "failed"
