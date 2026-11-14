package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder

/** What a client does with a form's target, as HAL-SCHEMA-FORMS-HR Rule 1 says. */
object Forms {
    private val QUERY = Regex("\\{\\?([^}]*)}")

    /**
     * [target] with its query template (`{?name,count}`) filled from [fields]: each named field that is there,
     * an array as the name repeated; a field not there is left out. A target with no query template is as given.
     */
    fun filled(target: String, fields: JsonNode): String = QUERY.replace(target) { match ->
        val pairs = match.groupValues[1].split(',').flatMap { name ->
            val value = fields.path(name)
            when {
                value.isMissingNode || value.isNull -> emptyList()
                value.isArray -> value.map { name to it.asText() }
                else -> listOf(name to value.asText())
            }
        }
        if (pairs.isEmpty()) "" else pairs.joinToString("&", "?") { (name, value) -> "$name=${encode(value)}" }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
