package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * Reads HAL-FORMS `_templates` (the on-ramp dialect) into the same
 * AffordanceForm model HAL Schema Forms produces. Each template's inline
 * `properties` array becomes a JSON Schema object, so the rest of ARTIE,
 * including the Reason step, sees one shape. The template key is kept as
 * the affordance name; a lone template is keyed "default" by HAL-FORMS.
 */
object HalFormsTemplates {
    const val TEMPLATES = "_templates"

    /** HAL-FORMS property keyword -> JSON Schema keyword, where they mean the same. */
    private val keywords = mapOf(
        "min" to "minimum",
        "max" to "maximum",
        "minLength" to "minLength",
        "maxLength" to "maxLength",
        "regex" to "pattern",
        "prompt" to "description",
        "readOnly" to "readOnly",
    )

    fun forms(root: JsonObject, base: String): Map<String, AffordanceForm> =
        root[TEMPLATES]?.jsonObject.orEmpty().mapValues { (rel, template) -> form(rel, template.jsonObject, base) }

    private fun form(rel: String, obj: JsonObject, base: String): AffordanceForm {
        val target = obj["target"]?.jsonPrimitive?.content ?: base
        return AffordanceForm(
            rel = rel,
            target = URI(base).resolve(target).toString(),
            templated = false,
            method = obj["method"]?.jsonPrimitive?.content ?: "GET",
            contentType = obj["contentType"]?.jsonPrimitive?.content ?: "application/json",
            schema = schema(obj["properties"] as? JsonArray ?: JsonArray(emptyList())),
        )
    }

    private fun schema(properties: JsonArray): JsonObject {
        val props = properties.map { it.jsonObject }
        val required = props.filter { it["required"]?.jsonPrimitive?.booleanOrNull == true }
            .map { JsonPrimitive(it.getValue("name").jsonPrimitive.content) }
        return JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                PROPERTIES to JsonObject(props.associate { it.getValue("name").jsonPrimitive.content to property(it) }),
                REQUIRED to JsonArray(required),
            ),
        )
    }

    private fun property(p: JsonObject): JsonObject {
        val type = if (p["type"]?.jsonPrimitive?.content == "number") "number" else "string"
        val mapped: Map<String, JsonElement> = keywords.mapNotNull { (from, to) -> p[from]?.let { to to it } }.toMap()
        return JsonObject(mapOf("type" to JsonPrimitive(type)) + mapped)
    }
}
