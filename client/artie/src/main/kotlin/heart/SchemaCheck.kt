package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks a selection's fields against an affordance's JSON Schema before
 * anything is sent (SPEC §6.3: "populate form fields within the constraints
 * declared by the schema"). A subset of JSON Schema: required, type
 * (string, integer, number, boolean), minimum, maximum, minLength,
 * maxLength, pattern, enum. One deliberate tightening: a field the schema
 * does not declare is refused, because the Reason step's output space is
 * meant to be closed. Returns one sentence per violation; empty means valid.
 */
object SchemaCheck {

    fun violations(fields: JsonObject, schema: JsonObject): List<String> {
        val properties = schema[PROPERTIES] as? JsonObject ?: JsonObject(emptyMap())
        val required = (schema[REQUIRED] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
        val missing = required.filter { it !in fields }.map { "$it is required" }
        val unknown = fields.keys.filter { it !in properties }.map { "$it is not in the schema" }
        val invalid = fields.flatMap { (name, value) ->
            properties[name]?.let { check(name, value, it.jsonObject) }.orEmpty()
        }
        return missing + unknown + invalid
    }

    private fun check(name: String, value: JsonElement, rules: JsonObject): List<String> {
        val primitive = value as? JsonPrimitive
        val typeError = typeError(name, primitive, rules["type"]?.jsonPrimitive?.content)
        if (typeError != null || primitive == null) return listOfNotNull(typeError)
        return bounds(name, primitive, rules) + text(name, primitive, rules) + choices(name, primitive, rules)
    }

    private fun typeError(name: String, value: JsonPrimitive?, type: String?): String? {
        val number = value?.takeUnless { it.isString }?.doubleOrNull
        val ok = when (type) {
            null -> true
            "string" -> value?.isString == true
            "integer" -> number != null && number % 1.0 == 0.0
            "number" -> number != null
            "boolean" -> value?.isString == false && value.content in setOf("true", "false")
            else -> true
        }
        return if (ok) null else "$name is not ${if (type == "integer") "an" else "a"} $type"
    }

    private fun bounds(name: String, value: JsonPrimitive, rules: JsonObject): List<String> {
        val number = value.takeUnless { it.isString }?.doubleOrNull ?: return emptyList()
        val min = rules["minimum"]?.jsonPrimitive?.doubleOrNull
        val max = rules["maximum"]?.jsonPrimitive?.doubleOrNull
        return listOfNotNull(
            min?.takeIf { number < it }?.let { "$name is below ${rules.getValue("minimum")}" },
            max?.takeIf { number > it }?.let { "$name is above ${rules.getValue("maximum")}" },
        )
    }

    private fun text(name: String, value: JsonPrimitive, rules: JsonObject): List<String> {
        if (!value.isString) return emptyList()
        val s = value.content
        val minLength = rules["minLength"]?.jsonPrimitive?.content?.toIntOrNull()
        val maxLength = rules["maxLength"]?.jsonPrimitive?.content?.toIntOrNull()
        val pattern = rules["pattern"]?.jsonPrimitive?.content
        return listOfNotNull(
            minLength?.takeIf { s.length < it }?.let { "$name is shorter than $it" },
            maxLength?.takeIf { s.length > it }?.let { "$name is longer than $it" },
            pattern?.takeIf { !Regex(it).containsMatchIn(s) }?.let { "$name does not match $it" },
        )
    }

    private fun choices(name: String, value: JsonPrimitive, rules: JsonObject): List<String> {
        val options = (rules["enum"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: return emptyList()
        return if (value.content in options) emptyList() else listOf("$name is not one of $options")
    }
}
