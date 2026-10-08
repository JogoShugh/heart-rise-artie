package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 6570 Level 1 expansion under HAL-SCHEMA-FORMS-HR Rule 1 (SPEC §6.9.1).
 * A variable takes its value from the first of:
 *  1. its `hr:templatePointers` JSON Pointer into the owning Layer 1 state,
 *     and nothing else if a pointer is declared;
 *  2. the form field of the same name (HAL Schema Forms);
 *  3. the Layer 1 top-level property of the same name (the JSON Hyper-Schema
 *     default).
 * The value must be a string or a number. Values are percent-encoded except
 * unreserved characters.
 */
object UriTemplate {
    private val variable = Regex("""\{([^}]+)}""")

    fun expand(
        template: String,
        state: JsonObject,
        pointers: Map<String, String> = emptyMap(),
        fields: JsonObject = JsonObject(emptyMap()),
    ): String =
        variable.replace(template) { match ->
            val name = match.groupValues[1]
            val pointer = pointers[name]
            val value = when {
                pointer != null -> JsonPointer.resolve(state, pointer)
                fields.containsKey(name) -> fields[name]
                else -> state[name]
            } as? JsonPrimitive
            require(value != null && (value.isString || value.content.toDoubleOrNull() != null)) {
                val source = pointer?.let { "its template pointer \"$it\"" } ?: "a field or Layer 1 property '$name'"
                "template variable {$name} in $template has no string or number value from $source"
            }
            encode(value.content)
        }

    private fun encode(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") c.toString() else "%%%02X".format(byte)
        }
}
