package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * An act's JSON Schema as HAL-FORMS template properties, for every template
 * heart-core renders: a resource's acts and the service index's. Each top-level
 * property becomes one HAL-FORMS property: its title is the prompt, and the
 * schema keywords that HAL-FORMS also has carry over (`minimum` as `min`,
 * `pattern` as `regex`, an `enum` as inline `options`). The schema stays the
 * authority: the body is checked against it, whatever the template says.
 */
object HalFormsProperties {
    private val mapper = ObjectMapper()

    /** Schema keyword -> HAL-FORMS keyword, where they mean the same. */
    private val keywords = mapOf(
        "minimum" to "min",
        "maximum" to "max",
        "minLength" to "minLength",
        "maxLength" to "maxLength",
        "pattern" to "regex",
    )

    fun of(schema: String): List<Map<String, Any>> {
        val root = mapper.readTree(schema)
        val required = root.path("required").map { it.asText() }.toSet()
        return root.path("properties").properties().map { (name, property) ->
            property(name, property, name in required)
        }
    }

    private fun property(name: String, schema: JsonNode, required: Boolean): Map<String, Any> = buildMap {
        put("name", name)
        schema.path("title").takeIf { it.isTextual }?.let { put("prompt", it.asText()) }
        put("required", required)
        put("type", inputType(schema.path("type").asText()))
        keywords.forEach { (from, to) -> schema.get(from)?.let { put(to, plain(it)) } }
        schema.get("enum")?.let { values ->
            put(
                "options",
                mapOf(
                    "inline" to values.map { it.asText() },
                    "minItems" to if (required) 1 else 0,
                    "maxItems" to 1,
                ),
            )
        }
    }

    /** HAL-FORMS types are HTML input types. */
    private fun inputType(jsonType: String): String = when (jsonType) {
        "integer", "number" -> "number"
        "boolean" -> "checkbox"
        else -> "text"
    }

    private fun plain(node: JsonNode): Any = when {
        node.isIntegralNumber -> node.asLong()
        node.isNumber -> node.asDouble()
        else -> node.asText()
    }
}
