package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * Reads HAL-FORMS `_templates` (the on-ramp dialect) into the same
 * AffordanceForm model HAL Schema Forms produces. Each template's inline
 * `properties` array becomes a JSON Schema object, so the rest of ARTIE,
 * including the Reason step, sees one shape. The template key is kept as
 * the affordance name, whatever the server chose: a HEART server uses the
 * act's name, another may key a lone template "default" as HAL-FORMS suggests.
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

    /**
     * Where a GET, HEAD or DELETE template goes: its target with the query
     * replaced by the fields, form-urlencoded (the HTML mutate-action-URL
     * algorithm the HAL-FORMS spec names). A space becomes "+". A field with
     * several values repeats its name for each, as a multiple select does.
     */
    fun getTarget(target: String, fields: JsonObject): String {
        val query = fields.entries.flatMap { (name, value) ->
            valuesOf(value).map { "${formEncode(name)}=${formEncode(it.content)}" }
        }.joinToString("&")
        return target.substringBefore('?') + if (query.isEmpty()) "" else "?$query"
    }

    /** A field's values as a form submits them: one, or each of a list's; objects have none. */
    private fun valuesOf(value: JsonElement): List<JsonPrimitive> = when (value) {
        is JsonPrimitive -> listOf(value)
        is JsonArray -> value.filterIsInstance<JsonPrimitive>()
        else -> emptyList()
    }

    /** application/x-www-form-urlencoded: letters, digits and "*-._" stay, a space is "+", the rest is %XX. */
    private fun formEncode(text: String): String =
        text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            when {
                byte >= 0 && (c.isLetterOrDigit() || c in "*-._") -> c.toString()
                c == ' ' -> "+"
                else -> "%%%02X".format(byte)
            }
        }

    fun forms(root: JsonObject, base: String): Map<String, AffordanceForm> {
        val templates = root[TEMPLATES]?.jsonObject.orEmpty().mapValues { (rel, t) -> form(rel, t.jsonObject, base) }
        return lookups(root, base).filterKeys { it !in templates } + templates
    }

    /**
     * The lookups a HAL-FORMS answer offers as link templates (RFC 6570), since its targets cannot carry variables:
     * each read as the GET form it is in all but name, by its relation, its fields the template's variables.
     */
    private fun lookups(root: JsonObject, base: String): Map<String, AffordanceForm> =
        (root[LINKS] as? JsonObject).orEmpty()
            .filterKeys { it !in PLUMBING }
            .mapNotNull { (rel, link) ->
                (link as? JsonObject)?.takeIf(::isLookup)?.let { rel to lookup(rel, it, base) }
            }
            .toMap()

    private fun isLookup(link: JsonObject): Boolean =
        link["templated"]?.jsonPrimitive?.booleanOrNull == true &&
            (link["type"]?.jsonPrimitive?.contentOrNull ?: HAL_JSON) in HEART_TYPES

    private fun lookup(rel: String, link: JsonObject, base: String): AffordanceForm {
        val href = link["href"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val text = JsonObject(mapOf("type" to JsonPrimitive("string")))
        val variables = UriTemplate.names(href).associateWith { text }
        return AffordanceForm(
            rel = rel,
            target = HeartResponseParser.resolveTemplate(href, base),
            templated = true,
            method = "GET",
            contentType = "application/json",
            schema = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    PROPERTIES to JsonObject(variables),
                    REQUIRED to JsonArray(emptyList()),
                ),
            ),
            title = link["title"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private const val LINKS = "_links"
    private val PLUMBING = setOf("self", "curies")
    private val HEART_TYPES = setOf(HAL_JSON, Dialect.HAL_FORMS.mediaType)

    private fun form(rel: String, obj: JsonObject, base: String): AffordanceForm {
        val target = obj["target"]?.jsonPrimitive?.content ?: base
        return AffordanceForm(
            rel = rel,
            // A HAL-FORMS target is never a template: a brace in it is a character like any other, sent encoded.
            target = URI(base).resolve(target.replace("{", "%7B").replace("}", "%7D")).toString(),
            templated = false,
            // An HTTP method is case-sensitive; Spring HATEOAS writes a template's in lower case.
            method = (obj["method"]?.jsonPrimitive?.content ?: "GET").uppercase(),
            contentType = obj["contentType"]?.jsonPrimitive?.content ?: "application/json",
            schema = schema(obj["properties"] as? JsonArray ?: JsonArray(emptyList())),
        )
    }

    /** The schema the properties say. An entry that is no object, or names no property, says nothing. */
    private fun schema(properties: JsonArray): JsonObject {
        val named = properties.filterIsInstance<JsonObject>()
            .mapNotNull { p -> (p["name"] as? JsonPrimitive)?.contentOrNull?.let { it to p } }
        val required = named.filter { (_, p) -> p["required"]?.jsonPrimitive?.booleanOrNull == true }
            .map { (name, _) -> JsonPrimitive(name) }
        return JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                PROPERTIES to JsonObject(named.associate { (name, p) -> name to property(p) }),
                REQUIRED to JsonArray(required),
            ),
        )
    }

    /**
     * A HAL-FORMS `type` is an HTML input type: a hint for how to ask, not a limit on the value
     * (a checkbox's value is a boolean, and a number may come as text), so it says nothing here.
     * Its `options`, written inline or linked, are its choices (HAL-SCHEMA-FORMS-HR Rule 8).
     */
    private fun property(p: JsonObject): JsonObject {
        val mapped: Map<String, JsonElement> = keywords.mapNotNull { (from, to) -> p[from]?.let { to to it } }.toMap()
        val options = (p["options"] as? JsonObject)?.let { mapOf(Choices.KEYWORD to it) }.orEmpty()
        return JsonObject(mapped + options)
    }
}
