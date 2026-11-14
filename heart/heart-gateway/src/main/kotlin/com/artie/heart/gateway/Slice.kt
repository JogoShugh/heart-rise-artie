package com.artie.heart.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

/**
 * A description cut down to the operations a connector uses, before it is
 * parsed and its references resolved: each path keeps only those operations,
 * and a path with none is left out. Components stay; only what the kept
 * operations reach is resolved.
 */
internal object Slice {
    private val json = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory())

    /** [text], a JSON or YAML description, keeping only the operations [include] names; all of it when none. */
    fun of(text: String, include: Set<OperationId>?): String {
        if (include == null) return text
        val tree = (if (text.trimStart().startsWith("{")) json else yaml).readTree(text) as ObjectNode
        val paths = tree.path("paths") as? ObjectNode ?: tree.putObject("paths")
        paths.properties().forEach { (_, item) ->
            (item as ObjectNode).properties().removeIf { (method, operation) ->
                method in METHODS && operation.path("operationId").asText() !in include
            }
        }
        paths.properties().removeIf { (_, item) -> item.properties().none { it.key in METHODS } }
        return json.writeValueAsString(tree)
    }

    private val METHODS = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
}
