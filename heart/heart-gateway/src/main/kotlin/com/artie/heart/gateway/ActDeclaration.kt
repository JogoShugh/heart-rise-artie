package com.artie.heart.gateway

import com.artie.heart.StatePointer
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * An act a resource file declares as a projection of one upstream
 * [operation]: GitHub's close is pulls/update with [fixed] `{"state":
 * "closed"}`. A client fills only [fields]; each of [bound] is filled from
 * the state the client's validator matched, never by the client, as merge's
 * `sha` from `/head/sha`. [stale] is the upstream's status for a bound field
 * that no longer matches, so the client's view is stale (412); [refused] is
 * its status for an act it won't do now (409), with its own words.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class ActDeclaration(
    val name: String,
    val operation: OperationId,
    val title: String,
    val description: String,
    val fixed: Map<String, Any?>,
    val fields: List<String>,
    val bound: Map<String, StatePointer>,
    val stale: Int?,
    val refused: Int?,
) {
    companion object {
        /** The act [name] in [node], from the file [label], or a refusal naming it. */
        fun of(label: String, name: String, node: JsonNode): ActDeclaration {
            fun text(field: String): String =
                checkNotNull(node.path(field).asText(null)) { "$label: act $name has no $field" }
            @Suppress("UNCHECKED_CAST")
            val fixed = MAPPER.convertValue(node.path("body"), Map::class.java) as Map<String, Any?>? ?: emptyMap()
            return ActDeclaration(
                name = name,
                operation = text("operation"),
                title = text("title"),
                description = text("description"),
                fixed = fixed,
                fields = node.path("fields").map { it.asText() },
                bound = node.path("bind").fields().asSequence().associate { (field, at) ->
                    field to runCatching { JsonPointer.compile(at.asText()).toString() }
                        .getOrElse { error("$label: ${at.asText()} is not a JSON Pointer") }
                },
                stale = node.path("stale").takeIf { it.isInt }?.asInt(),
                refused = node.path("refused").takeIf { it.isInt }?.asInt(),
            )
        }

        private val MAPPER = ObjectMapper()
    }
}
