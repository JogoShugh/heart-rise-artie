package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * RFC 6570 Level 1 expansion under SPEC §6.9.1's [0.5] rule: `{name}` takes
 * the Layer 1 top-level property `name`, which must be a string or a number.
 * There is no other lookup, so two clients always expand a template the
 * same way. Values are percent-encoded except unreserved characters.
 */
object UriTemplate {
    private val variable = Regex("""\{([^}]+)}""")

    fun expand(template: String, state: JsonObject): String =
        variable.replace(template) { match ->
            val name = match.groupValues[1]
            val value = state[name] as? JsonPrimitive
            require(value != null && (value.isString || value.content.toDoubleOrNull() != null)) {
                "template variable {$name} in $template has no string or number property '$name' in Layer 1"
            }
            encode(value.content)
        }

    private fun encode(value: String): String =
        value.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") c.toString() else "%%%02X".format(byte)
        }
}
