package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 6570 expansion under HAL-SCHEMA-FORMS-HR Rule 1 (SPEC §6.9.1): simple
 * `{name}` (Level 1) and form-style query `{?a,b}` (Level 3). A variable takes
 * its value from the first of:
 *  1. its `hr:templatePointers` JSON Pointer into the owning Layer 1 state,
 *     and nothing else if a pointer is declared;
 *  2. the form field of the same name (HAL Schema Forms);
 *  3. the Layer 1 top-level property of the same name (the JSON Hyper-Schema
 *     default).
 * The value must be a string or a number. A simple variable with no value is
 * an error; a query variable with no value is left out, as RFC 6570 says.
 * Values are percent-encoded except unreserved characters.
 */
object UriTemplate {
    private val expression = Regex("""\{(\??)([^}]+)}""")

    fun expand(
        template: String,
        state: JsonObject,
        pointers: Map<String, String> = emptyMap(),
        fields: JsonObject = JsonObject(emptyMap()),
    ): String {
        val values = Values(template, state, pointers, fields)
        return expression.replace(template) { match ->
            val names = match.groupValues[2].split(',').map { it.trim() }
            if (match.groupValues[1] == "?") {
                names.mapNotNull { name -> values.find(name)?.let { "$name=${encode(it)}" } }
                    .takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
            } else {
                encode(values.require(names.single()))
            }
        }
    }

    private class Values(
        val template: String,
        val state: JsonObject,
        val pointers: Map<String, String>,
        val fields: JsonObject,
    ) {
        fun find(name: String): String? {
            val pointer = pointers[name]
            val value = when {
                pointer != null -> JsonPointer.resolve(state, pointer)
                fields.containsKey(name) -> fields[name]
                else -> state[name]
            } as? JsonPrimitive
            return value?.takeIf { it.isString || it.content.toDoubleOrNull() != null }?.content
        }

        fun require(name: String): String = requireNotNull(find(name)) {
            val source = pointers[name]?.let { "its template pointer \"$it\"" } ?: "a field or Layer 1 property '$name'"
            "template variable {$name} in $template has no string or number value from $source"
        }
    }

    private fun encode(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") c.toString() else "%%%02X".format(byte)
        }
}
