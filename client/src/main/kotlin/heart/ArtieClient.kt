package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URI

/**
 * ARTIE's deterministic core: everything between a selection (an
 * affordance name and field values) and a binding (SPEC §6.0, §6.8). It never
 * builds a URI of its own; targets come from the manifest, and templates
 * are expanded as each form declares (HAL-SCHEMA-FORMS-HR Rule 1, see UriTemplate).
 * It treats every URI as opaque, and reaches each through the binding its
 * scheme selects: [transport] carries the HTTP binding, for `http` and `https`;
 * CoapBinding serves `coap` (SPEC §6.0.1); and [bindings] adds others or
 * replaces these. For caching and integrity checks, give it a
 * transport built as CachingTransport(IntegrityCheck(wire)).
 */
class ArtieClient(
    transport: Transport,
    private val rung: Rung = Rung.L0,
    private val dialect: Dialect = Dialect.HAL_SCHEMA_FORMS,
    /** Follow forms links and schema references that carry no integrity value (older servers only). */
    allowUncheckedLinks: Boolean = false,
    /** Where ARTIE reports its decisions; off by default. */
    events: EventLog = EventLog.OFF,
    /** Bindings for other URI schemes, by scheme (SPEC §6.0). */
    bindings: Map<String, Binding> = emptyMap(),
) : HeartRiseClient {
    init {
        require(rung <= dialect.maxRung) {
            "$dialect is served at ${dialect.maxRung} only (SPEC §6.1); rung $rung is not available in it"
        }
    }

    private val bindings = HttpBinding(transport).let { http ->
        Bindings(mapOf("http" to http, "https" to http, "coap" to CoapBinding()) + bindings)
    }
    private val decisions = DecisionLog(events)
    private val layers = SharedLayers(this.bindings, allowUncheckedLinks, decisions)

    override var current: HeartRepresentation? = null
        private set

    override var appliedRung: Rung? = null
        private set

    /** The rendering asked for: the client's own ([dialect], [rung]), or less if discovery found less. */
    private var asking = Rendering(dialect, rung)

    override fun discover(url: String) {
        val outcome = bindings.forUri(url).fetch(url, listOf(HomeDocument.MEDIA_TYPE))
        HomeDocument.offer(outcome)?.let { offer ->
            val (dialect, rung) = HomeDocument.choose(offer, dialect, rung)
            asking = Rendering(dialect, rung)
        }
    }

    override fun enter(url: String): HeartRepresentation = sync(url)

    /**
     * A GET form is safe (RFC 9110 §9.2.1): its target, filled from the form's
     * fields, is synced like any resource, with no body and no validator.
     * Every other method is a state-changing act.
     */
    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel)
            ?: run {
                decisions.absent("affordance", rel, held.url)
                error("No affordance '$rel' in current manifest")
            }
        // HAL-SCHEMA-FORMS-HR Rule 8: a value outside the form's choices is never sent.
        choices(rel)?.violations(payload)?.takeIf { it.isNotEmpty() }?.let { throw NotAChoice(rel, it) }
        if (form.method != "GET") return act(rel, payload, resendAllowed = true)
        // HAL Schema Forms fills the target template from the fields. HAL-FORMS cannot
        // template a target; it puts the fields in the query (mutate-action-URL).
        val target = when (asking.dialect) {
            Dialect.HAL_FORMS -> HalFormsTemplates.getTarget(form.target, payload)
            Dialect.HAL_SCHEMA_FORMS -> targetOf(form, held, payload)
        }
        return sync(target)
    }

    /** Rule 6: a choice the representation offers, picked by relation and title. ARTIE builds no URI. */
    override fun follow(rel: String, title: String?): HeartRepresentation {
        val held = current ?: error("enter a service before following a link")
        val link = held.links(rel).firstOrNull { it.title == title }
            ?: run {
                decisions.absent("link", rel, held.url)
                error("No '$rel' link titled '$title' in ${held.url}")
            }
        return sync(link.href)
    }

    override fun done() = decisions.done()

    override fun describe(rel: String): String? {
        val held = current ?: error("enter a service before describing a form")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        return Alps.describe(bindings, form.relation)
    }

    /** A plain JSON resource a form's choices come from, read as HTTP allows; null if it is not there. */
    override val listReader: (String) -> JsonElement? = { url ->
        (this.bindings.forUri(url).fetch(url, listOf(JSON_MEDIA_TYPE)) as? Outcome.Done)
            ?.let { runCatching { Json.parseToJsonElement(it.received.body) }.getOrNull() }
    }

    override fun explain(rel: String): String? {
        val held = current ?: error("enter a service before asking why an act is absent")
        if (held.affordance(rel) != null) return null
        return held.manifest.unavailable?.let { layers.reason(it, held.manifest.relationOf(rel)) }
    }

    /**
     * One act and its refusal handling (SPEC §6.3). Precondition required is
     * the only refusal that resends, and only once: the client always sends a
     * validator, so a second one would mean something else is wrong.
     */
    private fun act(rel: String, payload: JsonObject, resendAllowed: Boolean): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val validator = held.etag ?: error("no validator held for ${held.url}; an act needs one")
        val act = Act(targetOf(form, held, payload), form.method, form.contentType, payload.toString(), validator)
        val outcome = bindings.forUri(act.url).act(act, asking)
        decisions.actSent(rel, act, held.url, outcome)
        // SPEC §6.0, "New resource": the act brought a resource into being, and the client holds it next:
        // the answer itself when it represents that resource, else read at its own address.
        if (outcome is Outcome.Done) return outcome.newResource?.let { made ->
            if (outcome.represents == made) sync(made, outcome.received) else sync(made)
        } ?: sync(act.url, outcome.received)
        if (outcome is Outcome.PreconditionRequired && resendAllowed) {
            sync(held.url)
            decisions.actRefused(rel, act.url, outcome, "re-synced and resent once")
            return act(rel, payload, resendAllowed = false)
        }
        throw refusal(rel, act.url, outcome, held)
    }

    /** Stale and not offered re-sync first, so the client never keeps a manifest the server has refused. */
    private fun refusal(rel: String, target: String, outcome: Outcome, held: HeartRepresentation): RuntimeException {
        val (refused, handled) = when (outcome) {
            is Outcome.Unauthenticated -> outcome.refusal(target) to "reported"
            is Outcome.Denied -> outcome.refusal(target) to "reported"
            is Outcome.Stale -> StaleAct(rel).also { sync(held.url) } to "discarded after re-sync"
            is Outcome.NotOffered -> IllegalTransition(rel).also { sync(held.url) } to "re-synced and reported"
            is Outcome.Invalid if outcome.problemType == RULE_REFUSED ->
                RuleRefused(rel, outcome.violations) to "reported"
            is Outcome.Invalid -> InvalidPayload(rel, outcome.violations) to "reported"
            else -> UnexpectedResponse(rel, outcome.code) to "reported"
        }
        decisions.actRefused(rel, target, outcome, handled)
        return refused
    }

    /**
     * SPEC §6.7. At L0 one read. At L1/L2 a walk: Layer 1, then the
     * affordance set it links to, then each schema the set points to by
     * `$ref` (or an older server's `$id`), each URL once. Each fetch carries
     * the integrity value of the link it follows. A binding's cache makes a
     * URL already held cost nothing; reasoning waits until the manifest is
     * complete. An act's answer is Layer 1 too ([received]), and is completed
     * the same way.
     */
    private fun sync(url: String, received: Received? = null): HeartRepresentation {
        val layer1Received = received ?: bindings.forUri(url).read(url, asking).received(url)
        val layer1 = HeartResponseParser.parse(layer1Received, url, asking.dialect)
        // SPEC §6.11.1: the rung is the one the representation says it is at, read
        // before any link is followed. At L0 there is no set, whatever the body links.
        val applied = layer1Received.applied
        decisions.rung(url, layer1Received.appliedAs, applied)
        decisions.forms(url, layer1.formsLink, applied)
        val synced = layer1.formsLink?.takeIf { applied != Rung.L0 }?.let { link ->
            layer1.withManifest(layers.manifest(link, layer1.formsIntegrity))
        } ?: layer1
        appliedRung = applied
        return synced.also { current = it }
    }

    private companion object {
        /** SPEC §6.2: the problem type of a 422 whose body was valid but a rule refused. */
        const val RULE_REFUSED = ProblemDetails.BASE + "rule-refused"

        /** What a list of choices is read as (HAL-SCHEMA-FORMS-HR Rule 8). */
        const val JSON_MEDIA_TYPE = "application/json"

        fun targetOf(form: AffordanceForm, held: HeartRepresentation, fields: JsonObject): String {
            if (!form.templated) return form.target
            val expanded = UriTemplate.expand(form.target, held.state, form.templatePointers, fields)
            return URI(held.url).resolve(expanded).toString()
        }
    }
}
