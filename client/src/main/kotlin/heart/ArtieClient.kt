package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * ARTIE's deterministic core: everything between a selection (an
 * affordance name and field values) and the wire (SPEC §6.8). It never
 * builds a URI of its own; targets come from the manifest, and templates
 * are expanded as each form declares (HAL-SCHEMA-FORMS-HR Rule 1, see UriTemplate).
 * For caching and integrity checks, give it a transport built as
 * CachingTransport(IntegrityCheck(wire)). It treats every URI as opaque.
 */
class ArtieClient(
    private val transport: Transport,
    private val rung: Rung = Rung.L0,
    private val dialect: Dialect = Dialect.HAL_SCHEMA_FORMS,
    /** Follow forms links and schema references that carry no integrity value (older servers only). */
    allowUncheckedLinks: Boolean = false,
) : HeartRiseClient {
    init {
        require(rung <= dialect.maxRung) {
            "$dialect is served at ${dialect.maxRung} only (SPEC §6.1); rung $rung is not available in it"
        }
    }

    private val layers = SharedLayers(transport, allowUncheckedLinks)

    override var current: HeartRepresentation? = null
        private set

    override var appliedRung: Rung? = null
        private set

    /** The dialect and rung asked for: the client's own ([dialect], [rung]), or less if discovery found less. */
    private var asking: Pair<Dialect, Rung> = dialect to rung

    /**
     * The headers that select a representation: sent on every sync and every
     * act. HTTP is stateless, so an act repeats them; the server then selects
     * the same representation and compares If-Match with its ETag, whole
     * (RFC 9110 §13.1.1).
     */
    private val negotiation: Map<String, String>
        get() = buildMap {
            val (dialect, rung) = asking
            put("Accept", dialect.mediaType)
            put("Accept-Profile", "<${dialect.profile}>")
            rung.prefer?.let { put("Prefer", it) }
        }

    override fun discover(url: String) {
        val response = transport.send(HeartRequest("GET", url, mapOf("Accept" to HomeDocument.MEDIA_TYPE)))
        HomeDocument.offer(response)?.let { asking = HomeDocument.choose(it, dialect, rung) }
    }

    override fun enter(url: String): HeartRepresentation = sync(url)

    /**
     * A GET form is safe (RFC 9110 §9.2.1): its target, filled from the form's
     * fields, is synced like any resource, with no body and no If-Match.
     * Every other method is a state-changing act.
     */
    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        if (form.method != "GET") return act(rel, payload, resendAllowed = true)
        // HAL Schema Forms fills the target template from the fields. HAL-FORMS cannot
        // template a target; it puts the fields in the query (mutate-action-URL).
        val target = when (asking.first) {
            Dialect.HAL_FORMS -> HalFormsTemplates.getTarget(form.target, payload)
            Dialect.HAL_SCHEMA_FORMS -> targetOf(form, held, payload)
        }
        return sync(target)
    }

    /** Rule 6: a choice the representation offers, picked by relation and title. ARTIE builds no URI. */
    override fun follow(rel: String, title: String?): HeartRepresentation {
        val held = current ?: error("enter a service before following a link")
        val link = held.links(rel).firstOrNull { it.title == title }
            ?: error("No '$rel' link titled '$title' in ${held.url}")
        return sync(link.href)
    }

    override fun describe(rel: String): String? {
        val held = current ?: error("enter a service before describing a form")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        return Alps.describe(transport, form.relation)
    }

    override fun explain(rel: String): String? {
        val held = current ?: error("enter a service before asking why an act is absent")
        if (held.affordance(rel) != null) return null
        return held.manifest.unavailable?.let { layers.reason(it, held.manifest.relationOf(rel)) }
    }

    /**
     * One act and its refusal handling (SPEC §6.3). 428 is the only refusal
     * that resends, and only once: the client always sends If-Match, so a
     * second 428 would mean something else is wrong.
     */
    private fun act(rel: String, payload: JsonObject, resendAllowed: Boolean): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val etag = held.etag ?: error("no ETag held for ${held.url}; an act needs If-Match")
        val target = targetOf(form, held, payload)
        val response = transport.send(
            HeartRequest(
                form.method,
                target,
                negotiation + mapOf("Content-Type" to form.contentType, "If-Match" to etag),
                payload.toString(),
            ),
        )
        if (response.status in SUCCESS) return sync(target, response)
        if (response.status == PRECONDITION_REQUIRED && resendAllowed) {
            sync(held.url)
            return act(rel, payload, resendAllowed = false)
        }
        throw refusal(rel, response, held)
    }

    /** 412 and 409 re-sync first, so the client never keeps a manifest the server has refused. */
    private fun refusal(rel: String, response: HeartResponse, held: HeartRepresentation): ActRefused =
        when (response.status) {
            PRECONDITION_FAILED -> StaleAct(rel).also { sync(held.url) }
            CONFLICT -> IllegalTransition(rel).also { sync(held.url) }
            UNPROCESSABLE -> InvalidPayload(rel, HeartResponseParser.violations(response))
            else -> UnexpectedResponse(rel, response.status)
        }


    /**
     * SPEC §6.7. At L0 one GET. At L1/L2 a walk: Layer 1, then the
     * affordance set it links to, then each schema the set points to by
     * `$ref` (or an older server's `$id`), each URL once. Each fetch carries
     * the integrity value of the link it follows, checked below the cache.
     * The transport's HTTP cache makes a URL already held cost nothing;
     * reasoning waits until the manifest is complete. An act's response is
     * Layer 1 too ([received]), and is completed the same way.
     */
    private fun sync(url: String, received: HeartResponse? = null): HeartRepresentation {
        // SPEC §6.3: Layer 1 is never fresh. The request directive binds every cache
        // on the path (RFC 9111 §5.2.1.4), whatever the stored response said.
        val response = received ?: transport.send(HeartRequest("GET", url, negotiation + LAYER1_NO_CACHE))
        val layer1 = HeartResponseParser.parse(response, url, asking.first)
        // SPEC §6.11.1: the rung comes from Preference-Applied, read before any
        // link is followed. At L0 (no header) there is no set, whatever the body links.
        val applied = Rung.applied(response.header("Preference-Applied"))
        val synced = layer1.formsLink?.takeIf { applied != Rung.L0 }?.let { link ->
            layer1.withManifest(layers.manifest(link, layer1.formsIntegrity))
        } ?: layer1
        appliedRung = applied
        return synced.also { current = it }
    }

    private companion object {
        val LAYER1_NO_CACHE = mapOf("Cache-Control" to "no-cache")

        fun targetOf(form: AffordanceForm, held: HeartRepresentation, fields: JsonObject): String {
            if (!form.templated) return form.target
            val expanded = UriTemplate.expand(form.target, held.state, form.templatePointers, fields)
            return URI(held.url).resolve(expanded).toString()
        }

        val SUCCESS = 200..299
        const val CONFLICT = 409
        const val PRECONDITION_FAILED = 412
        const val UNPROCESSABLE = 422
        const val PRECONDITION_REQUIRED = 428
    }
}
