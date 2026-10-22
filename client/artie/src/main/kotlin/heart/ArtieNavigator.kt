package heart

import heart.script.Answer
import heart.script.Choice
import heart.script.Embedded
import heart.script.Navigator
import heart.script.Reasoned
import heart.script.Refusal
import heart.script.Representation
import heart.script.Side
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ARTIE as heart-script's client (spec/PLAN-ARTIE-SCRIPTS.md, "Not ARTIE's: a
 * contract"): a script's selections become ARTIE's acts and links, and its
 * refusals the DSL's kinds, each saying whether ARTIE refused it, from the
 * manifest, or the server did. Everything between a selection and the wire
 * stays ARTIE's: targets, methods, validators, bindings, credentials.
 */
class ArtieNavigator(
    private val client: HeartRiseClient,
    /** For `reason(…)` steps; null when the run allows none. */
    private val reasoner: Reasoner? = null,
    /** Why there is no [reasoner], when the run asked for one. */
    private val noReasoner: String = "this client has no reasoner",
    /** Where ARTIE keeps its exchanges for a run's capture; null when the run doesn't ask for them. */
    private val exchangeLog: ExchangeLog? = null,
    /** The script's lock file, held to before each act; null when the run is not guarded. */
    private val guard: ScriptGuard? = null,
) : Navigator {

    override fun exchanges(): List<heart.script.Exchange> = exchangeLog?.taken().orEmpty()

    /** A goal run's planner: Claude's, when the reasoner is (spec/PLAN-PLAIN-TEXT-RUN.md). */
    override fun planner(): heart.script.Planner? = (reasoner as? ClaudeReasoner)?.planner()

    /** Why there is no planner: the reason there is no reasoner, such as where the model key goes. */
    override val noPlanner: String get() = noReasoner

    override fun enter(entry: String): Answer = answer("entry") { client.enter(entry) }

    /**
     * The lock file first: a form that is not the one pinned is the guard's refusal, whatever its fields. Then the
     * fields are checked against the form's schema (SPEC §6.3), as ARTIE's reasoner path does: a value the schema
     * doesn't allow is refused here, by the client. Either way nothing is sent.
     */
    override fun act(rel: String, fields: Map<String, Any?>): Answer {
        val body = JsonObject(fields.mapValues { (_, v) -> json(v) })
        val used = UsedAffordances.form(client.current, rel, body, client.appliedRung, guard)
        val form = client.current?.affordance(rel)
        val violations = form?.let { SchemaCheck.violations(body, it.schema) }.orEmpty()
        val answered = guard?.refusal(client.current, rel, body)
            ?: violations.takeIf { it.isNotEmpty() }
                ?.let { Answer.Refused(Refusal.InvalidInput, Side.CLIENT, it.joinToString("; ")) }
            ?: answer(rel) { client.act(rel, body) }.let {
                if (it is Answer.Done && client.broughtIntoBeing) Answer.NewResource(it.now) else it
            }
        return UsedAffordances.with(answered, used)
    }

    override fun follow(rel: String, title: String?): Answer {
        val used = UsedAffordances.link(client.current, rel, title, guard)
        // The lock file, before anything is fetched: a refusal here is the guard's, by the client.
        val answered = guard?.linkRefusal(client.current, rel, title) ?: answer(rel) { client.follow(rel, title) }
        return UsedAffordances.with(answered, used)
    }

    override fun visit(address: String): Answer = answer(address) { client.enter(address) }

    override fun why(rel: String): String? = client.explain(rel)

    override fun describe(rel: String): String? = client.describe(rel)

    /**
     * ARTIE's Sync-Reason-Act, once (SPEC §6.8): the reasoner chooses from what is offered, ARTIE checks the
     * choice against the representation and the form's schema, then makes it. A rejected choice is refused here,
     * by the client, and nothing is sent.
     */
    override fun reason(intent: String): Reasoned {
        val thinker = reasoner
        val at = client.current?.url
        if (thinker == null || at == null) {
            val why = if (thinker == null) Refusal.NoReasoner to noReasoner else Refusal.NotOffered to "nothing is held"
            return Reasoned(null, Answer.Refused(why.first, Side.CLIENT, why.second))
        }
        var decided: Decision? = null
        val before = client.current
        val rung = client.appliedRung
        val keeps = thinker as? KeepsModelCalls
        keeps?.taken()
        val failed = answer("reason") { decided = ArtieAgent(client, thinker).handle(intent, at); client.current!! }
        val reasoned = when (val d = decided) {
            null -> Reasoned(null, failed)
            is Decision.Acted -> Reasoned(choice(d.selection), done(d.result, client.broughtIntoBeing, client.reaches))
            is Decision.Followed -> Reasoned(choice(d.selection), Answer.Done(representation(d.result, client.reaches)))
            is Decision.Refused -> Reasoned(choice(d.selection), refusal(client, d.selection.affordance, d.refusal))
            is Decision.Rejected -> Reasoned(choice(d.selection), rejected(d.reasons))
            is Decision.Asked -> Reasoned(null, Answer.Refused(Refusal.Unclear, Side.CLIENT, d.question))
            is Decision.Explained ->
                Reasoned(null, Answer.Refused(Refusal.NotOffered, Side.SERVER, d.reason ?: "no reason given"))
            is Decision.Selected -> error("ARTIE makes every choice it accepts")
        }
        // The affordance the reasoner's choice used, as the representation it chose from offered it.
        val used = UsedAffordances.chosen(before, decided, rung)
        return Reasoned(reasoned.chosen, UsedAffordances.with(reasoned.answer, used), keeps?.taken().orEmpty())
    }

    private fun answer(what: String, select: () -> HeartRepresentation): Answer = try {
        Answer.Done(representation(select(), client.reaches))
    } catch (refused: ActRefused) {
        refusal(client, what, refused)
    } catch (refused: AccessRefused) {
        refusal(client, what, refused)
    } catch (failed: java.io.IOException) {
        // Java's HTTP client says nothing when nothing listens: a ConnectException with no message.
        Answer.Refused(Refusal.Unreachable, Side.CLIENT, failed.message ?: "could not reach the server")
    } catch (refused: NoBindingException) {
        // SPEC §6.0 rule 4: a scheme ARTIE has no binding for is refused before anything is sent.
        Answer.Refused(Refusal.NoBinding, Side.CLIENT, refused.message.orEmpty())
    } catch (failed: IntegrityException) {
        Answer.Refused(Refusal.IntegrityMismatch, Side.CLIENT, failed.message.orEmpty())
    }
}

/** ARTIE's refusals, as the DSL's kinds; a reason the server gave for an absent act is asked of [client]. */
private fun refusal(client: HeartRiseClient, what: String, refused: RuntimeException): Answer.Refused =
    when (refused) {
        // ARTIE refuses an act its manifest lacks with an IllegalStateException; the DSL checks first.
        is StaleAct -> Answer.Refused(Refusal.Stale, Side.SERVER, "the resource changed since it was read")
        is IllegalTransition ->
            Answer.Refused(Refusal.NotOffered, Side.SERVER, client.explain(what) ?: "no reason given")
        // The server's 422: the body broke the form's schema, in the server's own check.
        is InvalidPayload -> Answer.Refused(Refusal.InvalidInput, Side.SERVER, refused.violations.joinToString("; "))
        is RuleRefused -> Answer.Refused(Refusal.InvalidInput, Side.SERVER, refused.violations.joinToString("; "))
        is NotAChoice -> Answer.Refused(Refusal.NotAChoice, Side.CLIENT, refused.violations.joinToString("; "))
        is NotAuthenticated -> Answer.Refused(Refusal.NotAuthenticated, Side.SERVER, refused.message.orEmpty())
        is Forbidden -> Answer.Refused(Refusal.Forbidden, Side.SERVER, refused.message.orEmpty())
        else -> throw refused
    }

/** A reasoner's selection, as a script's trace records the choice. */
private fun choice(selection: Selection): Choice? = when (selection) {
    is Selection.Act -> Choice(selection.affordance, selection.fields.mapValues { (_, v) -> plain(v) })
    is Selection.Follow -> Choice(selection.rel, follow = true, title = selection.title)
    else -> null
}

/** What an accepted act led to: a new resource when it brought one into being ([made]). */
private fun done(result: HeartRepresentation, made: Boolean, reaches: (HeartLink) -> Boolean): Answer =
    representation(result, reaches).let { if (made) Answer.NewResource(it) else Answer.Done(it) }

/** A choice ARTIE's checks rejected: not offered when nothing matched it, else input the schema doesn't allow. */
private fun rejected(reasons: List<String>): Answer.Refused {
    val absent = reasons.any { "not in the current manifest" in it || it.startsWith("no ") }
    val kind = if (absent) Refusal.NotOffered else Refusal.InvalidInput
    return Answer.Refused(kind, Side.CLIENT, reasons.joinToString("; "))
}

/** A JSON value, as a script's trace shows it. */
private fun plain(value: JsonElement): Any? {
    val primitive = value as? JsonPrimitive ?: return value.toString()
    return if (primitive.isString) primitive.content else primitive.toString()
}

/**
 * What ARTIE holds, as a script sees it: state, the acts offered, the links by relation and title, and the
 * resources it embeds (SPEC §6.1); and, for a model, only the links it [reaches], `self` and the forms link,
 * ARTIE's plumbing, left out.
 */
private fun representation(held: HeartRepresentation, reaches: (HeartLink) -> Boolean) = Representation(
    address = held.url,
    state = held.state,
    offered = held.affordances.keys,
    links = held.linksByRel.mapValues { (_, links) -> links.map { it.title } },
    embedded = held.embeddedByRel.mapValues { (_, resources) ->
        resources.map { Embedded(it.title, it.state, it.href) }
    },
    followable = held.linksByRel.filterKeys { it != "self" }
        .mapValues { (_, links) -> links.filter { it != held.forms && reaches(it) }.map { it.title } }
        .filterValues { it.isNotEmpty() },
)

/** A script's field value, as JSON. */
private fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { json(it) })
    else -> JsonPrimitive(value.toString())
}
