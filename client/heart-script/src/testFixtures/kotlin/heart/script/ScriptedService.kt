package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A form of the scripted service: at [at], [relation] leads to [to], as [outcome] ("done" or "new resource"). */
data class Form(val at: String, val relation: String, val to: String, val outcome: String)

/** A link of the scripted service: at [at], [relation] titled [title] leads to [to]. */
data class Link(val at: String, val relation: String, val title: String?, val to: String)

/**
 * A HEART service in a table, and a client of it, as a Navigator: resources
 * by address, their forms and links. Every call is recorded, as the client
 * would have been asked it, so a feature can say what a script did.
 */
class ScriptedService : Navigator {
    val resources = mutableMapOf<String, Map<String, String>>()
    val forms = mutableListOf<Form>()
    val links = mutableListOf<Link>()

    /** Resources one embeds (SPEC §6.1): at an address, by relation, the embedded resource's address. */
    val embeds = mutableListOf<Link>()

    /** A resource's whole state, by address, where a feature gives one: numbers, arrays, nested objects. */
    val bodies = mutableMapOf<String, JsonObject>()

    /** Whether the service, as a client, keeps the exchanges it makes for a run's capture. */
    var keepsExchanges = false

    /** The fields a form asks for, by "relation at address". */
    val fields = mutableMapOf<String, List<String>>()

    /** Refusals an act meets, from the server or the client, by "relation at address". */
    val refusals = mutableMapOf<String, Answer.Refused>()
    val asked = mutableListOf<Pair<String, String>>()

    /** States a resource takes after a number of further reads: address to (reads left, state). */
    val settles = mutableMapOf<String, Pair<Int, String>>()
    private var held = "/"

    override fun enter(entry: String): Answer {
        asked += "enter" to entry
        return at("/")
    }

    override fun act(rel: String, fields: Map<String, Any?>): Answer {
        asked += "act" to (if (fields.isEmpty()) rel else "$rel ${json(fields)}")
        refusals["$rel at $held"]?.let { return it }
        val form = forms.first { it.at == held && it.relation == rel }
        // As a HEART client reports the form it used: its target a template, as an affordance set holds it.
        val used = Used(
            "form", rel, method = "POST", target = held.replace(Regex("/\\d+"), "/{id}") + "/actions/$rel",
            address = "$held/actions/$rel", fields = this.fields["$rel at $held"].orEmpty(),
            guard = Guard(form = "sha256-$rel", ifMatch = "\"$held\""),
        )
        if (keepsExchanges) {
            val sent = Sent("POST", "scripted://service$held/actions/$rel", emptyMap(), json(fields))
            kept += Exchange(java.time.Instant.now(), 1, sent, Received(200, emptyMap(), null))
        }
        val now = representation(form.to).also { held = form.to }
        return if (form.outcome == "new resource") Answer.NewResource(now, used) else Answer.Done(now, used)
    }

    override fun follow(rel: String, title: String?): Answer {
        asked += "follow" to (if (title == null) rel else "$rel \"$title\"")
        val to = (links + embeds).first { it.at == held && it.relation == rel && it.title == title }.to
        val used = Used("link", rel, title, "GET", target = to, address = to)
        return Answer.Done(representation(to).also { held = to }, used)
    }

    override fun visit(address: String): Answer {
        asked += "return" to address
        settles[address]?.let { (left, state) ->
            if (left <= 1) {
                resources[address] = resources.getValue(address) + ("state" to state)
                settles.remove(address)
            } else {
                settles[address] = left - 1 to state
            }
        }
        return at(address)
    }

    override fun why(rel: String): String? = when (rel) {
        "reopen" -> "The issue is already open."
        else -> null
    }

    override fun describe(rel: String): String? = null

    private val kept = mutableListOf<Exchange>()

    override fun exchanges(): List<Exchange> = kept.toList().also { kept.clear() }

    /** What the reasoner chooses, by the words it is given. */
    val reasons = mutableMapOf<String, Choice>()

    override fun reason(intent: String): Reasoned {
        asked += "reason" to intent
        val chosen = reasons.getValue(intent)
        val answer = if (chosen.follow) follow(chosen.relation, chosen.title) else act(chosen.relation, chosen.fields)
        // As a client reports its model's calls: the scripted reasoner's one, asked the words, answering its choice.
        return Reasoned(chosen, answer, listOf(ModelCall("scripted-1", "intent: $intent", "chose: ${chosen.relation}")))
    }

    private fun at(address: String): Answer = Answer.Done(representation(address).also { held = address })

    private fun representation(address: String): Representation {
        val values = resources.getValue(address)
        val state = bodies[address]
            ?: buildJsonObject { values.filterValues { it.isNotEmpty() }.forEach { (k, v) -> put(k, v) } }
        return Representation(
            address,
            state,
            forms.filter { it.at == address }.map { it.relation }.toSet(),
            links.filter { it.at == address }.groupBy({ it.relation }, { it.title }),
            embeds.filter { it.at == address }.groupBy({ it.relation }, { Embedded(it.title, stateOf(it.to), it.to) }),
        )
    }

    private fun stateOf(address: String): JsonObject {
        val values = resources.getValue(address)
        return buildJsonObject { values.filterValues { it.isNotEmpty() }.forEach { (k, v) -> put(k, v) } }
    }

    private fun json(fields: Map<String, Any?>): String =
        JsonObject(fields.mapValues { (_, v) -> element(v) }).toString()

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { element(it) })
        else -> JsonPrimitive(value.toString())
    }
}
