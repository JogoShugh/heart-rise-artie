package com.artie.heart.gateway

import com.artie.heart.StatePointer
import com.artie.heart.UriTemplate
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64

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
    /** The resource, by its noun, that the operation's answer is: a repository's open-issue makes an issue. */
    val creates: String? = null,
    /**
     * Fields the client fills that are no member of the operation's body, each with its JSON Schema: the
     * [fixed] body's string values are templates of them, as `refs/heads/{name}` (decided 2026-10-18).
     */
    val inputs: Map<String, Map<String, Any?>> = emptyMap(),
    /** Fields the gateway encodes before sending, by encoding: `content` in base64. */
    val encode: Map<String, String> = emptyMap(),
    /**
     * Where the resource [creates] names is, when the operation's answer isn't it: a URI template filled from
     * this resource's path variables and the act's fields, as a new branch's `/repos/{owner}/{repo}/branches/{name}`.
     */
    val at: UriTemplate? = null,
    /** Fields the form names otherwise than the operation does, each with the operation's own name. */
    val rename: Map<String, String> = emptyMap(),
) {
    /** The operation's own name for the form's [field]: as written, or as [rename] maps it. */
    fun upstream(field: String): String = rename[field] ?: field

    /** A field's value as the upstream takes it: as the client wrote it, or encoded as the act says. */
    fun encoded(field: String, value: Any?): Any? =
        if (encode[field] == BASE64) Base64.getEncoder().encodeToString(value.toString().toByteArray()) else value

    /** The [fixed] body, each string value a template filled from the act's [inputs]: `refs/heads/{name}`. */
    fun filled(inputs: Map<String, String>): Map<String, Any?> = fixed.mapValues { (_, value) ->
        if (value is String) TEMPLATE.replace(value) { inputs[it.groupValues[1]] ?: it.value } else value
    }

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
                creates = node.path("creates").asText(null),
                inputs = node.path("inputs").properties().associate { (field, schema) ->
                    @Suppress("UNCHECKED_CAST")
                    field to (MAPPER.convertValue(schema, Map::class.java) as Map<String, Any?>)
                },
                encode = node.path("encode").properties().associate { (field, encoding) ->
                    check(encoding.asText() == BASE64) { "$label: act $name encodes $field as ${encoding.asText()}" }
                    field to encoding.asText()
                },
                at = node.path("at").asText(null),
                rename = node.path("rename").properties().associate { (field, upstream) ->
                    check(field in node.path("fields").map { it.asText() }) {
                        "$label: act $name renames $field, which is not one of its fields"
                    }
                    field to upstream.asText()
                },
            )
        }

        /** The one encoding a field may ask for. */
        const val BASE64 = "base64"

        private val TEMPLATE = Regex("\\{([A-Za-z0-9_]+)}")

        private val MAPPER = ObjectMapper()
    }
}
