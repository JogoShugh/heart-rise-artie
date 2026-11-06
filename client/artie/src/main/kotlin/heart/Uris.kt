package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Small rules for addresses a representation gives, shared by both dialects. */
internal object Uris {
    /** An address that is no template, kept as written: its braces are characters, and sent encoded. */
    fun braces(address: String): String = address.replace("{", "%7B").replace("}", "%7D")

    /**
     * [target] with each field the template did not take added to its query, a list's name once
     * for each value: how a safe form's fields leave (SPEC §6.2).
     */
    fun withQuery(target: String, taken: Set<String>, fields: JsonObject): String {
        val pairs = fields.filterKeys { it !in taken }.flatMap { (name, value) ->
            valuesOf(value).map { "${encode(name)}=${encode(it)}" }
        }
        if (pairs.isEmpty()) return target
        return target + (if ('?' in target) "&" else "?") + pairs.joinToString("&")
    }

    private fun valuesOf(value: JsonElement): List<String> = when (value) {
        is JsonArray -> value.filterIsInstance<JsonPrimitive>().map { it.content }
        is JsonPrimitive -> listOf(value.content)
        else -> emptyList()
    }

    private fun encode(text: String): String =
        text.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
            val c = byte.toInt().toChar()
            if (c.isLetterOrDigit() && byte >= 0 || c in "-._~") c.toString() else "%%%02X".format(byte)
        }
}
