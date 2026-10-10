package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * The resources a manifest links to rather than carries: the affordance set
 * (Layer 2), each schema (Layer 3), and the explanation of absent acts
 * (SPEC §6.12). Every one is fetched through a link that carries an
 * integrity value (HAL-SCHEMA-FORMS-HR Rule 2), checked below the cache, so
 * an HTTP cache may keep them for as long as they are served immutable.
 */
internal class SharedLayers(
    private val transport: Transport,
    /** Follow links that carry no integrity value (older servers only). */
    private val allowUncheckedLinks: Boolean,
    private val decisions: DecisionLog,
) {

    /**
     * The affordance set at [link], with each schema it points to by `$ref`
     * (or an older server's `$id`) fetched once and put in place.
     */
    fun manifest(link: String, integrity: String?): Manifest {
        val fetched = HeartResponseParser.manifest(json(link, integrity), link)
        val schemas = mutableMapOf<String, JsonObject>()
        return fetched.copy(
            forms = fetched.forms.mapValues { (_, form) ->
                val pointer = schemaPointer(form.schema) ?: return@mapValues form
                val schemaUrl = URI(link).resolve(pointer).toString()
                val pin = form.schema[SCHEMA_INTEGRITY]?.jsonPrimitive?.content
                form.copy(schema = schemas.getOrPut(schemaUrl) { json(schemaUrl, pin) })
            },
        )
    }

    /**
     * Why [relation] is absent, from the explanation [link] leads to, or
     * null if the list does not name it. Each listed relation is a URI
     * reference, resolved against the explanation's own URL.
     */
    fun reason(link: HeartLink, relation: String): String? =
        (json(link.href, link.integrity)["unavailable"] as? JsonArray).orEmpty().map { it.jsonObject }
            .firstOrNull { entry ->
                entry["rel"]?.jsonPrimitive?.content?.let { URI(link.href).resolve(it).toString() } == relation
            }
            ?.get("reason")?.jsonPrimitive?.content

    /**
     * Fetches a shared resource. Its link must carry an integrity value
     * unless [allowUncheckedLinks] is set for an older server; a link
     * without one is refused before any request.
     */
    fun json(url: String, integrity: String?): JsonObject {
        val accept = mapOf("Accept" to "$HAL_JSON, application/json")
        val response = try {
            if (integrity == null && !allowUncheckedLinks) {
                throw IntegrityException(
                    "$url was linked without an integrity value; refused (HAL-SCHEMA-FORMS-HR Rule 2)",
                )
            }
            transport.send(HeartRequest("GET", url, accept, integrity = integrity))
        } catch (refused: IntegrityException) {
            decisions.integrityRefused(url, integrity, refused.message)
            throw refused
        }
        integrity?.let { decisions.integrityChecked(url, it) }
        check(response.status in SUCCESS) { "GET $url answered ${response.status}" }
        return Json.parseToJsonElement(response.body).jsonObject
    }

    private companion object {
        /** A `$ref` to a separate schema (HAL-SCHEMA-FORMS-HR Rule 3), or an older server's lone `$id`. */
        fun schemaPointer(schema: JsonObject): String? =
            schema[SCHEMA_REF]?.jsonPrimitive?.content
                ?: schema.takeIf { it.keys == setOf(SCHEMA_ID) }?.get(SCHEMA_ID)?.jsonPrimitive?.content

        const val OK = 200
        const val LAST_SUCCESS = 299
        val SUCCESS = OK..LAST_SUCCESS
        const val SCHEMA_ID = "\$id"
        const val SCHEMA_REF = "\$ref"
        const val SCHEMA_INTEGRITY = "hr:integrity"
    }
}
