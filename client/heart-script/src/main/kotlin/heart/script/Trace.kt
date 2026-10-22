package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * One step of a run, in the heart-run-report trace format: when it began
 * and how long it took, what was asked as a sentence with `{name}` for each
 * value, the outcome by meaning, where the client then was ([address], for
 * the report only) and what it held there. No protocol in it.
 */
class TraceStep(
    val began: Began,
    val asked: Asked,
    val outcome: String,
    val address: String,
    val now: Representation?,
    val refused: Answer.Refused? = null,
) {
    /** What a test script expected this step to answer, in words (heart-script-test); null when nothing. */
    var expect: String? = null

    /** What an act was for, in words, as its script said (`act(rel, …, intent = …)`); null when it didn't. */
    var intent: String? = null

    /** The script line this step came from, as its author numbers it; null when run outside a script. */
    var line: Int? = null

    /** The line of the block this step ran in; null at the script's top level. */
    var within: Int? = null

    /** The affordance the client used for this step; null when it says nothing, or none was used. */
    var used: Used? = null

    /** What the representation the step chose from offered: its forms and its links' relations. */
    var offered: Representation? = null

    /** Each value sent, with every source it has (Sources). */
    var values: JsonObject? = null

    /** A reason step's template and its slots, when the script's text is known (Sources). */
    var reason: JsonObject? = null

    /** Whether the step opens a block: the script visits what it led to, then comes back. */
    var opens = false

    /** An until that held: what it waited for, as the script wrote it, and how many reads it took. */
    var waited: JsonObject? = null

    /** A reason step's calls to the reasoner's model: what it was asked and what it answered. */
    var model: List<ModelCall> = emptyList()

    /** The exchanges the client made for this step, when it keeps them. */
    var exchanges: List<Exchange> = emptyList()

    /** When a step began, and how long it took. */
    class Began(val at: Instant, val ms: Long)

    /**
     * What a step asked: a sentence with `{name}` for each value, and the values. A script step's sentence
     * names the selection; an intent's ([mode] "intent") is the words as said, its values the choice made.
     */
    class Asked(val say: String, val params: Map<String, Any?>, val mode: String = "script")

    fun json(): JsonObject = buildJsonObject {
        put("at", began.at.toString())
        put("ms", began.ms)
        put("mode", asked.mode)
        put("say", asked.say)
        intent?.let { put("intent", it) }
        line?.let { put("line", it) }
        within?.let { put("within", it) }
        put("params", JsonObject(asked.params.mapValues { (_, v) -> element(v) }))
        shape().forEach { (name, value) -> put(name, value) }
        put("outcome", outcome)
        expect?.let { put("expect", it) }
        put("address", address)
        now?.let { held ->
            put("now", buildJsonObject {
                put("state", held.state)
                put("forms", JsonArray(held.offered.map { JsonPrimitive(it) }))
                put("links", JsonObject(held.links.mapValues { (_, titles) -> JsonArray(titles.map { element(it) }) }))
                if (held.embedded.isNotEmpty()) put("embedded", embedded(held.embedded))
            })
        }
        refused?.let { r ->
            put("refused", buildJsonObject {
                put("kind", r.kind.words)
                put("by", r.by.words)
                put("detail", r.detail)
            })
        }
    }
}

/** What a step held embedded (SPEC §6.1): by relation, each resource's title and state. */
internal fun embedded(byRel: Map<String, List<Embedded>>): JsonObject = JsonObject(
    byRel.mapValues { (_, resources) ->
        JsonArray(resources.map { buildJsonObject { put("title", element(it.title)); put("state", it.state) } })
    },
)

/** A step the script itself takes, with no request: a check, or an act it refuses from the manifest. */
internal fun Run.record(
    asked: TraceStep.Asked,
    outcome: String,
    held: Representation,
    refused: Answer.Refused? = null,
) {
    val now = held.takeIf { refused == null }
    traced(TraceStep(TraceStep.Began(Instant.now(), 0), asked, outcome, held.address, now, refused))
}

/** What selecting the form [rel] with [fields] asks, whether it is sent or refused before it is. */
internal fun actAsked(rel: String, fields: Map<String, Any?>) = TraceStep.Asked(
    "act {relation}" + fields.keys.joinToString("") { ", $it {$it}" },
    mapOf("relation" to rel) + fields,
)

/** What following the link [rel] titled [title] asks, whether it is followed or refused before it is. */
internal fun followAsked(rel: String, title: String?) = TraceStep.Asked(
    "follow {relation}" + (if (title != null) ", titled {title}" else ""),
    mapOf("relation" to rel) + (title?.let { mapOf("title" to it) } ?: emptyMap()),
)

/** A trace's value, as JSON. */
internal fun element(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { element(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to element(v) })
    else -> JsonPrimitive(value.toString())
}

/** The outcome of [answer], by meaning. */
internal fun outcomeOf(answer: Answer): String = when (answer) {
    is Answer.Done -> "done"
    is Answer.NewResource -> "new resource"
    is Answer.Refused -> "refused"
}

/** A client whose every call is a step of [run]'s trace: so any client's run is traced alike. */
internal class TracingNavigator(private val inner: Navigator, private val run: Run) : Navigator {
    private var at = ""

    override fun enter(entry: String): Answer =
        traced({ TraceStep.Asked("enter {entry}", mapOf("entry" to entry)) }) { inner.enter(entry) }

    override fun act(rel: String, fields: Map<String, Any?>): Answer =
        traced({ actAsked(rel, fields) }) { inner.act(rel, fields) }

    override fun follow(rel: String, title: String?): Answer =
        traced({ followAsked(rel, title) }) { inner.follow(rel, title) }

    override fun visit(address: String): Answer =
        traced({ TraceStep.Asked("return to {address}", mapOf("address" to address)) }) { inner.visit(address) }

    override fun recall(address: String): Answer =
        traced({ TraceStep.Asked("return to {address}", mapOf("address" to address)) }) { inner.recall(address) }

    override fun why(rel: String): String? = inner.why(rel)

    override fun describe(rel: String): String? = inner.describe(rel)

    /** One intent step: the words, and the choice the reasoner made, known only once it has made it. */
    override fun reason(intent: String): Reasoned {
        var reasoned: Reasoned? = null
        val answer = traced({ reasonAsked(intent, reasoned?.chosen) }) {
            inner.reason(intent).also { reasoned = it }.answer
        }
        reasoned?.model?.takeIf { it.isNotEmpty() }?.let { run.trace.last().model = it }
        return Reasoned(reasoned?.chosen, answer, reasoned?.model.orEmpty())
    }

    /** Make the [call], and trace it as [asked], read after the call. */
    private fun traced(asked: () -> TraceStep.Asked, call: () -> Answer): Answer {
        val began = Instant.now()
        val started = System.nanoTime()
        val answer = call()
        val now = when (answer) {
            is Answer.Done -> answer.now
            is Answer.NewResource -> answer.now
            is Answer.Refused -> null
        }
        val from = held
        now?.let { at = it.address; held = it }
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        val step = TraceStep(TraceStep.Began(began, ms), asked(), outcomeOf(answer), at, now, answer as? Answer.Refused)
        step.used = answer.used
        step.offered = from.takeIf { answer.used != null }
        step.exchanges = inner.exchanges()
        run.traced(step)
        return answer
    }

    /** What the client held before the step: what a selection chose from. */
    private var held: Representation? = null

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
