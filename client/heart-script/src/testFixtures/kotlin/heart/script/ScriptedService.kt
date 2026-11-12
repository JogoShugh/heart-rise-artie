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
class ScriptedService : Navigator, Wire, Writes, Reads {
    val resources = mutableMapOf<String, Map<String, String>>()
    val forms = mutableListOf<Form>()
    val links = mutableListOf<Link>()

    /** Resources one embeds (SPEC §6.1): at an address, by relation, the embedded resource's address. */
    val embeds = mutableListOf<Link>()

    /** A resource's whole state, by address, where a feature gives one: numbers, arrays, nested objects. */
    val bodies = mutableMapOf<String, JsonObject>()

    /** Whether the service, as a client, keeps the exchanges it makes for a run's capture. */
    var keepsExchanges = false

    /** The JSON Schema type of each field a form asks for, by "relation at address". */
    val types = mutableMapOf<String, Map<String, String>>()

    /** The fields a form asks for, by "relation at address". */
    val fields = mutableMapOf<String, List<String>>()

    /** Refusals an act meets, from the server or the client, by "relation at address". */
    val refusals = mutableMapOf<String, Answer.Refused>()

    /** How many more times a refusal is met, by "relation at address"; absent, every time. */
    val refusedTimes = mutableMapOf<String, Int>()
    val asked = mutableListOf<Pair<String, String>>()

    /** States a resource takes after a number of further reads: address to (reads left, state). */
    val settles = mutableMapOf<String, Pair<Int, String>>()
    private var held = "/"

    override fun enter(entry: String): Answer {
        asked += "enter" to entry
        return at("/")
    }

    /** What happens to the process as the client acts, by relation, before it sends: a stop, where a feature says. */
    val onAct = mutableMapOf<String, () -> Unit>()

    override fun act(rel: String, fields: Map<String, Any?>, asOf: String?): Answer {
        onAct[rel]?.invoke()
        val version = asOf?.let { " as of \"$it\"" }.orEmpty()
        asked += "act" to (if (fields.isEmpty()) rel + version else "$rel ${json(fields)}$version")
        versions[held] = (versions[held] ?: 0) + 1
        val key = "$rel at $held"
        refusals[key]?.takeIf { (refusedTimes[key] ?: 1) > 0 }?.let { refusal ->
            refusedTimes[key]?.let { refusedTimes[key] = it - 1 }
            return refusal
        }
        val form = forms.first { it.at == held && it.relation == rel }
        // As a HEART client reports the form it used: its target a template, as an affordance set holds it.
        val used = Used(
            "form", rel, method = "POST", target = held.replace(Regex("/\\d+"), "/{id}") + "/actions/$rel",
            address = "$held/actions/$rel", fields = this.fields["$rel at $held"].orEmpty(),
            guard = Guard(form = "sha256-$rel", ifMatch = "\"$held\""),
            types = types["$rel at $held"].orEmpty(),
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

    /** What the server answers bare requests, in order: each by "METHOD url", a status and a body. */
    val answers = ArrayDeque<Pair<String, Received>>()

    /** Each bare call sent, as "METHOD url", with the headers it carried. */
    val sentHeaders = mutableListOf<Pair<String, Map<String, String>>>()

    override fun send(sent: Sent): Received {
        asked += "send" to listOfNotNull(sent.method, sent.url, sent.body).joinToString(" ")
        sentHeaders += "${sent.method} ${sent.url}" to sent.headers
        val (expected, answer) = answers.removeFirst()
        check(expected == "${sent.method} ${sent.url}") { "the server expected $expected" }
        return answer
    }

    /** What a link leads to, read as it is: the server's next answer, as a bare call's is. */
    override fun read(rel: String, title: String?): Received? {
        val to = links.firstOrNull { it.at == held && it.relation == rel && it.title == title }?.to ?: return null
        return send(Sent("GET", to, emptyMap(), null))
    }

    override fun why(rel: String): String? = when (rel) {
        "reopen" -> "The issue is already open."
        else -> null
    }

    override fun describe(rel: String): String? = null

    private val kept = mutableListOf<Exchange>()

    override fun exchanges(): List<Exchange> = kept.toList().also { kept.clear() }

    /** What the client counts its model calls took, when a scenario says. */
    var counted: Map<String, Long> = emptyMap()

    override val usage: Map<String, Long> get() = counted

    /** The model a scenario says this client reasons with, by name and url. */
    var named: Map<String, String> = emptyMap()

    override val model: Map<String, String> get() = named

    /** What the reasoner chooses, by the words it is given. */
    val reasons = mutableMapOf<String, Choice>()

    /** What the reasoner writes, by the words it is asked. */
    val writes = mutableMapOf<String, String>()

    override fun write(intent: String): String? {
        asked += "write" to intent
        return writes[intent]
    }

    /** A goal run's moves, in order; once they run out, finished. */
    val moves = ArrayDeque<Move>()

    /** Why this client has no planner, when a scenario says it has none. */
    var missingPlanner: String? = null

    override val noPlanner: String get() = missingPlanner ?: super.noPlanner

    override fun planner(): Planner? =
        if (missingPlanner != null) null else Planner { moves.removeFirstOrNull() ?: Move.Finish }

    /** What the reasoner chooses, by the words it is given and the state the resource it holds is in. */
    val reasonsHolding = mutableMapOf<Pair<String, String>, Choice>()

    /** What the reasoner asks instead of choosing, by the words it is given and the state it holds. */
    val asksHolding = mutableMapOf<Pair<String, String>, String>()

    /** What the run told the reasoner besides the words, each time it told it anything. */
    val told = mutableListOf<String>()

    /** What the reasoner chooses, told that what the words ask is not offered yet, by the words and the state. */
    val towardHolding = mutableMapOf<Pair<String, String>, Choice>()

    /** What the reasoner asks, told that what the words ask is not offered yet, by the words and the state. */
    val towardAsks = mutableMapOf<Pair<String, String>, String>()

    /** Why the server doesn't offer an act, by relation, where a feature says. */
    val whyNot = mutableMapOf<String, String>()

    override fun reason(intent: String, asOf: String?): Reasoned {
        // The run may tell the reasoner more after the words, as "<words>. <more>": the words are the step's.
        val words = (reasons.keys + reasonsHolding.keys.map { it.first } + asksHolding.keys.map { it.first })
            .firstOrNull { intent == it || intent.startsWith("$it. ") } ?: intent
        val besides = intent.removePrefix(words).removePrefix(". ")
        if (intent != words) told += besides
        asked += "reason" to words
        val holding = resources[held]?.get("state").orEmpty()
        val toward = besides.startsWith("What these words ask is not offered")
        (if (toward) towardAsks else asksHolding)[words to holding]?.let { question ->
            return Reasoned(null, Answer.Refused(Refusal.Unclear, Side.CLIENT, question))
        }
        val chosen = (if (toward) towardHolding[words to holding] else null)
            ?: reasonsHolding[words to holding] ?: reasons.getValue(words)
        // A choice the resource doesn't offer, where a feature gives the server's reason for it, is answered with
        // that reason, as an explain is: nothing sent.
        val why = whyNot[chosen.relation]
        if (why != null && !chosen.follow && forms.none { it.at == held && it.relation == chosen.relation }) {
            return Reasoned(chosen, Answer.Refused(Refusal.NotOffered, Side.SERVER, why))
        }
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
        ).also { it.validator = "$address@${versions[address] ?: 0}" }
    }

    /** How many times each resource was acted on: its version, as its validator names it. */
    private val versions = mutableMapOf<String, Int>()

    /** An embedded resource's state: its whole body where a feature gives one, as a held resource's is. */
    private fun stateOf(address: String): JsonObject {
        val values = resources.getValue(address)
        return bodies[address]
            ?: buildJsonObject { values.filterValues { it.isNotEmpty() }.forEach { (k, v) -> put(k, v) } }
    }

    private fun json(fields: Map<String, Any?>): String =
        JsonObject(fields.mapValues { (_, v) -> element(v) }).toString()

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { element(it) })
        is Map<*, *> -> JsonObject(value.entries.associate { (name, member) -> name.toString() to element(member) })
        else -> JsonPrimitive(value.toString())
    }
}
