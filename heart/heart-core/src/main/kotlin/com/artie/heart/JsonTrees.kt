package com.artie.heart

import jakarta.json.Json
import jakarta.json.JsonArray
import jakarta.json.JsonNumber
import jakarta.json.JsonObject
import jakarta.json.JsonString
import jakarta.json.JsonValue
import java.io.StringReader

/** JSON text as plain Kotlin trees: the maps, lists and scalars every binding serializes. */
object JsonTrees {
    fun plain(json: String): Any? = Json.createReader(StringReader(json)).use { plain(it.readValue()) }

    private fun plain(value: JsonValue): Any? = when (value) {
        is JsonObject -> value.entries.associateTo(LinkedHashMap()) { (k, v) -> k to plain(v) }
        is JsonArray -> value.map { plain(it) }
        is JsonString -> value.string
        is JsonNumber -> if (value.isIntegral) value.longValue() else value.doubleValue()
        JsonValue.TRUE -> true
        JsonValue.FALSE -> false
        else -> null
    }
}
