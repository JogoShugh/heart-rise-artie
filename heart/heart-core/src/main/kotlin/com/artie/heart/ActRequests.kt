package com.artie.heart

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * What every binding does with a request to an act, the same way (SPEC §6.2): whether its method is the act's,
 * and the act's input. A DELETE takes no body, so its input is its query, each value typed as the act's schema
 * types the field it names: a number, an integer, a boolean, or an array of them by repeating the name. A
 * value that isn't what its field's type says stays text, so the schema check refuses it at that field.
 */
object ActRequests {
    private val mapper = ObjectMapper()

    /** Whether [method] is the one [act] is served by. */
    fun allowed(act: Act, method: String): Boolean = method.equals(act.method, ignoreCase = true)

    /** The act's input: [body], or for a DELETE, its [query] as JSON. */
    fun input(act: Act, body: String?, query: Map<String, List<String>>): String? =
        if (act.method == Act.DELETE) fromQuery(act, query) else body

    /** The input of a DELETE whose raw query string is [rawQuery] (`tag=sale&count=2`). */
    fun input(act: Act, body: String?, rawQuery: String?): String? = input(act, body, parse(rawQuery))

    /** [rawQuery], each name with its values in order, decoded. */
    fun parse(rawQuery: String?): Map<String, List<String>> =
        rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map { pair ->
            val name = pair.substringBefore('=')
            val value = if ('=' in pair) pair.substringAfter('=') else ""
            decode(name) to decode(value)
        }.groupBy({ it.first }, { it.second })

    private fun fromQuery(act: Act, query: Map<String, List<String>>): String {
        val fields = query.mapValues { (name, values) ->
            val (type, items) = InputSchemas.propertyType(act.schema, name)
            if (type == "array") values.map { typed(it, items) } else typed(values.last(), type)
        }
        return mapper.writeValueAsString(LinkedHashMap(fields))
    }

    /** [text] as the JSON value [type] says, when it reads as one; as text otherwise. */
    private fun typed(text: String, type: String?): Any = when (type) {
        "integer" -> text.toLongOrNull() ?: text
        "number" -> text.toLongOrNull() ?: text.toDoubleOrNull() ?: text
        "boolean" -> text.toBooleanStrictOrNull() ?: text
        else -> text
    }

    private fun decode(text: String): String = URLDecoder.decode(text, StandardCharsets.UTF_8)
}
