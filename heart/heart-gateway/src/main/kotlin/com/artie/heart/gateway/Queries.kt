package com.artie.heart.gateway

import com.fasterxml.jackson.databind.JsonNode
import java.net.URLEncoder

/** A DELETE's input as the query it is sent with (SPEC §6.2). */
internal object Queries {
    /** `?name=bug&count=2` from [input]'s members, an array as its name repeated; empty when it has none. */
    fun of(input: JsonNode): String {
        val pairs = input.properties().flatMap { (name, value) ->
            when {
                value.isNull -> emptyList()
                value.isArray -> value.map { name to it.asText() }
                else -> listOf(name to value.asText())
            }
        }
        return if (pairs.isEmpty()) "" else pairs.joinToString("&", "?") { (name, value) -> "$name=${encode(value)}" }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
