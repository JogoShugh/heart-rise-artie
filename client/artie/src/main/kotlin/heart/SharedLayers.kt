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
 * integrity value (HAL-SCHEMA-FORMS-HR Rule 2). The core checks the bytes
 * against it whatever the binding; the HTTP binding also checks below its
 * cache, so refused bytes are never stored.
 */
internal class SharedLayers(
    private val bindings: Bindings,
    /** Follow links that carry no integrity value (older servers only). */
    private val allowUncheckedLinks: Boolean,
    private val decisions: DecisionLog,
) {

    /**
     * The affordance set at [link], with each schema it points to by `$ref`
     * fetched once and put in place.
     */
    fun manifest(link: String, integrity: String?): Manifest {
        val fetched = HeartResponseParser.manifest(json(link, integrity), link)
        val schemas = mutableMapOf<String, JsonObject>()
        return fetched.copy(
            forms = fetched.forms.mapValues { (_, form) ->
                val pointer = schemaPointer(form.schema) ?: return@mapValues form
                val schemaUrl = URI(link).resolve(pointer).toString()
                val pin = form.schema[SCHEMA_INTEGRITY]?.jsonPrimitive?.content
                form.copy(
                    schema = schemas.getOrPut(schemaUrl) { json(schemaUrl, pin) },
                    schemaRef = HeartLink(schemaUrl, integrity = pin),
                )
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
        val received = try {
            if (integrity == null && !allowUncheckedLinks) {
                throw IntegrityException(
                    "$url was linked without an integrity value; refused (HAL-SCHEMA-FORMS-HR Rule 2)",
                )
            }
            bindings.forUri(url).fetch(url, ACCEPT, integrity).received(url)
                .also { received -> integrity?.let { Integrity.checkLink(url, received.bytes, it) } }
        } catch (refused: IntegrityException) {
            decisions.integrityRefused(url, integrity, refused.message)
            throw refused
        }
        integrity?.let { decisions.integrityChecked(url, it) }
        return Json.parseToJsonElement(received.body).jsonObject
    }

    private companion object {
        /** A `$ref` to a separate schema (HAL-SCHEMA-FORMS-HR Rule 3). A lone `$id` names a schema; it links none. */
        fun schemaPointer(schema: JsonObject): String? = schema[SCHEMA_REF]?.jsonPrimitive?.content

        /** A shared layer is an affordance set or a schema: whatever it may be served as. */
        val ACCEPT = listOf(HAL_JSON, "application/schema+json", "application/json")
        const val SCHEMA_REF = "\$ref"
        const val SCHEMA_INTEGRITY = "hr:integrity"
    }
}
