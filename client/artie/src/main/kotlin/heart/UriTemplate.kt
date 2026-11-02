package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
        val values = Values(state, pointers, fields)
        return expression.replace(template) { match ->
            val names = match.groupValues[2].split(',').map { it.trim() }
            if (match.groupValues[1] == "?") {
                // A list's name is repeated for each of its values (RFC 6570 explode, as a form submits).
                names.flatMap { name -> values.all(name).map { "$name=${encode(it)}" } }
                    .takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
            } else {
                encode(values.require(names.single()))
            }
        }
    }

    /** The variables [template] takes, simple and query alike. */
    fun names(template: String): Set<String> =
        expression.findAll(template).flatMap { match -> match.groupValues[2].split(',').map { it.trim() } }.toSet()

    private class Values(
        val state: JsonObject,
        val pointers: Map<String, String>,
        val fields: JsonObject,
    ) {
        private fun valueOf(name: String): JsonElement? {
            val pointer = pointers[name]
            return when {
                pointer != null -> JsonPointer.resolve(state, pointer)
                fields.containsKey(name) -> fields[name]
                else -> state[name]
            }
        }

        private fun text(value: JsonElement?): String? = (value as? JsonPrimitive)
            ?.takeIf { it.isString || it.content.toDoubleOrNull() != null }?.content

        fun find(name: String): String? = text(valueOf(name))

        /** Each string or number value of [name]: one, or each of a list's. */
        fun all(name: String): List<String> = when (val value = valueOf(name)) {
            is JsonArray -> value.mapNotNull(::text)
            else -> listOfNotNull(text(value))
        }

        fun require(name: String): String = requireNotNull(find(name)) {
            "the template variable $name has no string or number value; nothing was sent"
        }
    }

    private fun encode(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") c.toString() else "%%%02X".format(byte)
        }
}
