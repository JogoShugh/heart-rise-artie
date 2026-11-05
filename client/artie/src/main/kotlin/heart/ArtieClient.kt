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
    /** What it takes care over: links with integrity, and validators on acts. */
    private val care: Care = Care(),
    /** Where ARTIE reports its decisions; off by default. */
    events: EventLog = EventLog.OFF,
    /** Bindings for other URI schemes, by scheme (SPEC §6.0). */
    bindings: Map<String, Binding> = emptyMap(),
) : HeartRiseClient {
    private val bindings = HttpBinding(transport).let { http ->
        Bindings(mapOf("http" to http, "https" to http, "coap" to CoapBinding()) + bindings)
    }
    private val decisions = DecisionLog(events)
    private val layers = SharedLayers(this.bindings, !care.checkedLinks, decisions)
    private val schemas = DeclaredSchemas(decisions)

    override var current: HeartRepresentation? = null
        private set

    override var appliedRung: Rung? = null
        private set

    override var broughtIntoBeing: Boolean = false
        private set

    /**
     * The rendering asked for: the client's own ([dialect], [rung], no higher than the dialect is
     * served: HAL-FORMS asks for no layering), or less if discovery found less.
     */
    private var asking = Rendering(dialect, minOf(rung, dialect.maxRung))

    override fun discover(url: String) {
        val outcome = bindings.forUri(url).fetch(url, listOf(HomeDocument.MEDIA_TYPE))
        HomeDocument.offer(outcome)?.let { offer ->
            val (dialect, rung) = HomeDocument.choose(offer, dialect, rung)
            asking = Rendering(dialect, rung)
        }
    }

    override fun enter(url: String): HeartRepresentation = sync(url)

    override val awaiting: (String, Long) -> HeartRepresentation = { url, seconds ->
        sync(url, this.bindings.forUri(url).read(url, asking, seconds).received(url))
    }

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
                error("$rel is not offered now; nothing was sent")
            }
        // HAL-SCHEMA-FORMS-HR Rule 8: a value outside the form's choices is never sent.
        choices(rel)?.violations(payload)?.takeIf { it.isNotEmpty() }?.let { throw NotAChoice(rel, it) }
        // SPEC §6.3: nor is a body the form's declared schema disallows, unless the run leaves that to the server.
        if (care.checkedBodies) {
            schemas.violations(form, payload, held.url).takeIf { it.isNotEmpty() }?.let { throw OutsideSchema(rel, it) }
        }
        broughtIntoBeing = false
        if (form.method != "GET") return send(rel, payload)
        // HAL Schema Forms fills the target template from the fields. HAL-FORMS cannot
        // template a target; it puts the fields in the query (mutate-action-URL).
        val target = when (asking.dialect) {
            Dialect.HAL_FORMS -> HalFormsTemplates.getTarget(form.target, payload)
            // A field the target's template does not take goes in its query. Plain JSON offers no form to get here.
            Dialect.HAL_SCHEMA_FORMS, Dialect.JSON -> {
                val taken = if (form.templated) UriTemplate.names(form.target) else emptySet()
                Uris.withQuery(targetOf(form, held, payload), taken, payload)
            }
        }
        return sync(target)
    }

    override val reaches: (HeartLink) -> Boolean = { link -> link.followable && this.bindings.reaches(link.href) }

    /** Each resource as last read or answered, by address. */
    private val held = mutableMapOf<String, HeartRepresentation>()

    override val recall: (String) -> HeartRepresentation = { url -> held[url]?.also { current = it } ?: enter(url) }

    /**
     * Rule 6: a choice the representation offers, picked by relation and title. ARTIE builds no URI.
     * No [title] means any title will do: the first of the relation is taken.
     */
    override fun follow(rel: String, title: String?): HeartRepresentation {
        var held = current ?: error("enter a service before following a link")
        fun matches(candidate: String?) = title == null || candidate == title
        // SPEC §6.1: an embedded resource is followed like a link; following fetches it.
        fun found(page: HeartRepresentation) = page.links(rel).firstOrNull { matches(it.title) }
            ?: page.embedded(rel).firstOrNull { matches(it.title) }?.self
        // A list paged by next links may hold it further on: each next page is followed, as the list offers it.
        var pages = 0
        while (found(held) == null && pages++ < MAX_PAGES) {
            held = held.links("next").firstOrNull()?.let { sync(it.href) } ?: break
        }
        val link = found(held)
            ?: run {
                decisions.absent("link", rel, held.url)
                error("no $rel link${title?.let { " titled $it" }.orEmpty()} is offered; nothing was sent")
            }
        return sync(link.href)
    }

    override fun done() = decisions.done()

    override fun describe(rel: String): String? {
        val held = current ?: error("enter a service before describing a form")
        // A relation not offered now says nothing: a goal run may ask about one it saw before the resource changed.
        val form = held.affordance(rel) ?: return null
        return Alps.describe(bindings, form.relation)
    }

    /** A plain JSON resource a form's choices come from, read as HTTP allows; null if it is not there. */
    override val listReader: (String) -> JsonElement? = { url ->
        (this.bindings.forUri(url).fetchFresh(url, listOf(JSON_MEDIA_TYPE)) as? Outcome.Done)
            ?.let { runCatching { Json.parseToJsonElement(it.received.body) }.getOrNull() }
    }

    override fun explain(rel: String): String? {
        val held = current ?: error("enter a service before asking why an act is absent")
        if (held.affordance(rel) != null) return null
        return held.manifest.unavailable?.let { layers.reason(it, held.manifest.relationOf(rel)) }
    }

    /** One act and its refusal handling (SPEC §6.3). No refusal is resent: each is told apart and reported. */
    private fun send(rel: String, payload: JsonObject): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("$rel is not offered now; nothing was sent")
        val validator = validatorFor(held)
        // A DELETE or a HEAD has no body: its fields fill its target's query, a template in HAL Schema Forms,
        // and the query itself in HAL-FORMS, as a GET's do (SPEC §6.2).
        val bodyless = form.method in BODYLESS
        val target = if (bodyless && asking.dialect == Dialect.HAL_FORMS) {
            HalFormsTemplates.getTarget(form.target, payload)
        } else {
            targetOf(form, held, payload)
        }
        val act = Act(target, form.method, form.contentType, payload.toString().takeUnless { bodyless }, validator)
        val outcome = bindings.forUri(act.url).act(act, asking)
        decisions.actSent(rel, act, held.url, outcome)
        broughtIntoBeing = outcome is Outcome.Done && outcome.newResource != null
        // A DELETE answered with no body removed the resource: it is held as gone, and not read again.
        val removed = form.method == "DELETE" && outcome is Outcome.Done && outcome.received.body.isBlank()
        if (removed) return held.asGone().also { current = it }
        if (outcome is Outcome.Done) return answered(outcome, act.url, held)
        throw refusal(rel, act.url, outcome, held)
    }

    /**
     * What an act's answer leaves held. SPEC §6.0, "New resource": a resource the act brought into being,
     * from the answer when it represents that resource, else read at its own address. Otherwise the
     * resource the answer names in Content-Location, or else the one acted on: from the answer's body,
     * or, when it has none, read again.
     */
    private val answered: (Outcome.Done, String, HeartRepresentation) -> HeartRepresentation = { outcome, acted, held ->
        val hasBody = outcome.received.body.isNotBlank()
        val made = outcome.newResource
        when {
            made != null && outcome.represents == made && hasBody -> sync(made, outcome.received)
            made != null -> sync(made)
            hasBody && outcome.represents != null -> sync(outcome.represents, outcome.received, named = true)
            hasBody -> sync(acted, outcome.received)
            else -> sync(outcome.represents ?: held.url)
        }
    }

    override val sendsValidators: Boolean get() = care.validators

    /** The validator an act on [held] carries: the one held, or none when the run sends blind writes. */
    private val validatorFor: (HeartRepresentation) -> String? = { held ->
        // A representation sent with no validator is acted on with an empty one: still conditional, never blind.
        if (care.validators) held.etag.orEmpty() else null
    }

    /** Stale and not offered re-sync first, so the client never keeps a manifest the server has refused. */
    private fun refusal(rel: String, target: String, outcome: Outcome, held: HeartRepresentation): RuntimeException {
        // Said as every client says a refusal: the act, the code, and what the problem said, if anything.
        val said = "$rel was refused (${outcome.code})" + outcome.said?.let { ": $it" }.orEmpty()
        val (refused, handled) = when (outcome) {
            is Outcome.Unauthenticated -> NotAuthenticated(target, outcome.schemes, outcome.sent, said) to "reported"
            is Outcome.Denied -> outcome.refusal(target) to "reported"
            is Outcome.Stale -> StaleAct(said).also { sync(held.url) } to "discarded after re-sync"
            is Outcome.NotOffered -> IllegalTransition(said).also { sync(held.url) } to "re-synced and reported"
            is Outcome.PreconditionRequired -> ValidatorRequired(said) to "reported"
            is Outcome.Invalid if outcome.problemType == RULE_REFUSED ->
                RuleRefused(said, outcome.violations) to "reported"
            is Outcome.Invalid -> InvalidPayload(said, outcome.violations) to "reported"
            is Outcome.Failed if outcome.code in OUT_OF_DATE && changedSince(held) ->
                StaleAct(said) to "the form was out of date: discarded after re-sync"
            else -> UnexpectedResponse(said, outcome.code) to "reported"
        }
        decisions.actRefused(rel, target, outcome, handled)
        return refused
    }

    /**
     * Whether the resource [held] has changed since it was read, read again now (a new ETag). A 404 or 405 from an
     * act's target is then the form's being out of date, the act moved or its method changed, and stale, as a 412 is.
     */
    private val changedSince: (HeartRepresentation) -> Boolean = { held ->
        held.etag != null && sync(held.url).etag != held.etag
    }

    /**
     * SPEC §6.7. At L0 one read. At L1/L2 a walk: Layer 1, then the
     * affordance set it links to, then each schema the set points to by
     * `$ref`, each URL once. Each fetch carries
     * the integrity value of the link it follows. A binding's cache makes a
     * URL already held cost nothing; reasoning waits until the manifest is
     * complete. An act's answer is Layer 1 too ([received]), and is completed
     * the same way.
     */
    private fun sync(url: String, received: Received? = null, named: Boolean = false): HeartRepresentation {
        val layer1Received = received ?: bindings.forUri(url).read(url, asking).received(url)
        // A client reads whichever dialect comes back, by its media type; else the one it asked for.
        val dialect = Dialect.entries.firstOrNull { it.mediaType == layer1Received.mediaType } ?: asking.dialect
        val layer1 = HeartResponseParser.parse(layer1Received, url, dialect, named = url.takeIf { named })
        // SPEC §6.11.1: the rung is the one the representation says it is at, read
        // before any link is followed. At L0 there is no set, whatever the body links.
        val applied = layer1Received.applied
        decisions.rung(url, layer1Received.appliedAs, applied)
        decisions.forms(url, layer1.formsLink, applied)
        // SPEC §6.7: at L1 and L2 the acts offered are in the set; with no link to it they cannot be known.
        check(layer1.formsLink != null || applied == Rung.L0) { "$url applied $applied but links no affordance set" }
        val synced = layer1.formsLink?.takeIf { applied != Rung.L0 }?.let { link ->
            layer1.withManifest(layers.manifest(link, layer1.formsIntegrity))
        } ?: layer1
        appliedRung = applied
        // Kept by every address it answers for, for a run off the rails to come back to with no read.
        (listOf(url, synced.url) + synced.links("self").map { it.href }).forEach { held[it] = synced }
        return synced.also { current = it }
    }

    private companion object {
        /** The most next pages a follow reads to find a titled link. */
        const val MAX_PAGES = 20

        /** SPEC §6.2: the problem type of a 422 whose body was valid but a rule refused. */
        const val RULE_REFUSED = ProblemDetails.BASE + "rule-refused"

        /** The methods whose acts carry no body. */
        val BODYLESS = setOf("DELETE", "HEAD")

        /** What an act's target answers when the form it came from is out of date: moved, or another method now. */
        val OUT_OF_DATE = setOf("404", "405", "410", "4.04", "4.05")

        /** What a list of choices is read as (HAL-SCHEMA-FORMS-HR Rule 8). */
        const val JSON_MEDIA_TYPE = "application/json"
    }
}

/** The address [form] leads to from [held], its template filled from the state and [fields] (Rule 1). */
internal fun targetOf(form: AffordanceForm, held: HeartRepresentation, fields: JsonObject): String {
    if (!form.templated) return form.target
    val expanded = UriTemplate.expand(form.target, held.state, form.templatePointers, fields)
    return URI(held.url).resolve(expanded).toString()
}
