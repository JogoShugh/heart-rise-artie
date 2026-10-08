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
 * are expanded from Layer 1 by the one [0.5] rule (see UriTemplate).
 * For caching and integrity checks, give it a transport built as
 * CachingTransport(IntegrityCheck(wire)). It treats every URI as opaque.
 */
class ArtieClient(
    private val transport: Transport,
    private val rung: Rung = Rung.L0,
    private val dialect: Dialect = Dialect.HAL_SCHEMA_FORMS,
) : HeartRiseClient {
    init {
        require(rung <= dialect.maxRung) {
            "$dialect is served at ${dialect.maxRung} only (SPEC §6.1); rung $rung is not available in it"
        }
    }

    override var current: HeartRepresentation? = null
        private set

    override fun enter(url: String): HeartRepresentation = sync(url)

    override fun act(rel: String, payload: JsonObject): HeartRepresentation = act(rel, payload, resendAllowed = true)

    /**
     * One act and its refusal handling (SPEC §6.3). 428 is the only refusal
     * that resends, and only once: the client always sends If-Match, so a
     * second 428 would mean something else is wrong.
     */
    private fun act(rel: String, payload: JsonObject, resendAllowed: Boolean): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val etag = held.etag ?: error("no ETag held for ${held.url}; an act needs If-Match")
        val target = targetOf(form, held)
        val response = transport.send(
            HeartRequest(
                form.method,
                target,
                mapOf("Content-Type" to form.contentType, "Accept" to dialect.mediaType, "If-Match" to etag),
                payload.toString(),
            ),
        )
        if (response.status in SUCCESS) {
            return HeartResponseParser.parse(response, target, dialect).also { current = it }
        }
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

    private fun targetOf(form: AffordanceForm, held: HeartRepresentation): String {
        if (!form.templated) return form.target
        return URI(held.url).resolve(UriTemplate.expand(form.target, held.state)).toString()
    }

    /**
     * SPEC §6.7. At L0 one GET. At L1/L2 a walk: Layer 1, then the
     * affordance set it links to, then each schema the set points to by
     * `$ref` (or an older server's `$id`), each URL once. Each fetch carries
     * the integrity value of the link it follows, checked below the cache.
     * The transport's HTTP cache makes a URL already held cost nothing;
     * reasoning waits until the manifest is complete.
     */
    private fun sync(url: String): HeartRepresentation {
        val headers = buildMap {
            put("Accept", dialect.mediaType)
            put("Accept-Profile", "<${dialect.profile}>")
            rung.prefer?.let { put("Prefer", it) }
        }
        val layer1 = HeartResponseParser.parse(transport.send(HeartRequest("GET", url, headers)), url, dialect)
        val synced = layer1.formsLink?.let { link ->
            layer1.withAffordances(fetchSet(link, layer1.formsIntegrity))
        } ?: layer1
        return synced.also { current = it }
    }

    private fun fetchSet(link: String, integrity: String?): Map<String, AffordanceForm> {
        val forms = HeartResponseParser.forms(fetchJson(link, integrity), link)
        val schemas = mutableMapOf<String, JsonObject>()
        return forms.mapValues { (_, form) ->
            val pointer = schemaPointer(form.schema)
            if (pointer == null) {
                form
            } else {
                val schemaUrl = URI(link).resolve(pointer).toString()
                val pin = form.schema[SCHEMA_INTEGRITY]?.jsonPrimitive?.content
                form.copy(schema = schemas.getOrPut(schemaUrl) { fetchJson(schemaUrl, pin) })
            }
        }
    }

    /** A `$ref` to a separate schema (HAL-SCHEMA-FORMS-HR Rule 3), or an older server's lone `$id`. */
    private fun schemaPointer(schema: JsonObject): String? =
        schema[SCHEMA_REF]?.jsonPrimitive?.content
            ?: schema.takeIf { it.keys == setOf(SCHEMA_ID) }?.get(SCHEMA_ID)?.jsonPrimitive?.content

    private fun fetchJson(url: String, integrity: String?): JsonObject {
        val accept = mapOf("Accept" to "$HAL_JSON, application/json")
        val response = transport.send(HeartRequest("GET", url, accept, integrity = integrity))
        check(response.status in SUCCESS) { "GET $url answered ${response.status}" }
        return Json.parseToJsonElement(response.body).jsonObject
    }

    private companion object {
        val SUCCESS = 200..299
        const val SCHEMA_ID = "\$id"
        const val SCHEMA_REF = "\$ref"
        const val SCHEMA_INTEGRITY = "hr:integrity"
        const val CONFLICT = 409
        const val PRECONDITION_FAILED = 412
        const val UNPROCESSABLE = 422
        const val PRECONDITION_REQUIRED = 428
    }
}
