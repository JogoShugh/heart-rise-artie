package heart.script

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

/**
 * A bare script (spec/ideas/bare-runs.md): a recorded run brought down to the calls it made, every guardrail off.
 * Each [Bare.call] is sent as written, to the address written, by the client the run is given: its credentials
 * and its trace, nothing else.
 */
fun bare(block: Bare.() -> Unit) {
    val run = Run.current
    if (run.listing) return
    run.checkInputs()
    val client = run.navigator()
    val wire = client as? Wire ?: error("this client sends no bare requests")
    Bare(wire, client, run).block()
}

/**
 * A client that also sends bare requests: [send] sends one as it is, to the address it names, and gives back the
 * answer as it came. Nothing a HEART client does is done: no entry, no form, no validator, no cache, no check;
 * only the client's credentials and its trace.
 */
fun interface Wire {
    fun send(sent: Sent): Received
}

/** Where a bare script is: nowhere. It holds no representation; it only sends, and reads what came back. */
@HeartDsl
class Bare internal constructor(private val wire: Wire, internal val navigator: Navigator, internal val run: Run) {
    /** The member of a `whenever` whose turn it is, by its key; null outside one. */
    internal var member: String? = null

    /**
     * Send [method] to [url] with [body] as JSON, and answer with what came back, or null for an empty answer.
     * Any answer but a 2xx stops the script; with [retry] above 1, the call is tried again, a second apart, that
     * many times in all, first: for a call the recorded run waited before.
     */
    fun call(method: String, url: String, body: Any? = null, retry: Int = 1): JsonElement? {
        val step = run.step + 1
        var answer: Received? = null
        for (attempt in 1..retry) {
            if (attempt > 1) run.pause(1.seconds)
            answer = sent(method, url, body)
            if (answer.status in OK) return parsed(answer)
        }
        val last = checkNotNull(answer)
        throw ScriptStopped(step, "\"$method $url\"", "the server answered ${last.status}: ${last.body.orEmpty()}")
    }

    /** Send [method] to [url] with [body] once, as a step of its own; answer with what came back, whatever it is. */
    internal fun sent(method: String, url: String, body: Any?): Received {
        ++run.step
        val sent = Sent(method, url, CONTENT, body?.let { element(it).toString() })
        val began = Instant.now()
        return wire.send(sent).also { trace(sent, it, began) }
    }

    /** What [answer] holds, as JSON; null for an empty one. */
    internal fun parsed(answer: Received): JsonElement? =
        answer.body?.takeIf { it.isNotBlank() }?.let(Json::parseToJsonElement)

    private fun trace(sent: Sent, answer: Received, began: Instant) {
        val asked = TraceStep.Asked("send {method} {url}", mapOf("method" to sent.method, "url" to sent.url))
        val outcome = if (answer.status in OK) "done" else "refused"
        val ms = Duration.between(began, Instant.now()).toMillis()
        val step = TraceStep(TraceStep.Began(began, ms), asked, outcome, sent.url, null)
        step.exchanges = navigator.exchanges()
        run.traced(step)
    }

    internal companion object {
        val OK = 200..299
        val CONTENT = mapOf("Content-Type" to "application/json", "Accept" to "application/json")
    }
}

/** The member [key] of an answer read by a bare script; null when it has none. */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/** The item at [index] of an answer read by a bare script; null when it has none. */
operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/** A value read from an answer, as text: a number's digits, a string's characters. */
val JsonElement?.text: String get() = (this as? JsonPrimitive)?.content ?: error("not a value: $this")

/** [text] in base64, as GitHub's contents API takes a file. */
fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())
