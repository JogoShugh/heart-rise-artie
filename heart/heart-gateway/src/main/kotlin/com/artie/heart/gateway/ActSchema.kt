package com.artie.heart.gateway

import com.artie.heart.ItemPath

/**
 * The JSON Schema of an act's form: the fields the client fills, each as the
 * operation describes it. A field is found in the body's properties, in an
 * object branch of the body's `oneOf` or `anyOf` (GitHub's add-labels takes
 * an object or a bare array), or among the operation's path parameters the
 * resource's state doesn't fill (remove-label's `{name}`), which the gateway
 * then puts in the path.
 */
internal class ActSchema(private val act: ActDeclaration, private val route: SpecRoute, private val file: String) {

    @Suppress("UNCHECKED_CAST")
    private val body: Map<String, Any?> = route.body.orEmpty()

    /** The form's fields that fill the operation's path variables: those the state's pointers don't. */
    fun pathFields(filled: Set<String>): List<String> {
        val variables = ItemPath.variablesOf(route.path).filter { it !in filled }
        return act.fields.filter { act.upstream(it) in variables }
    }

    fun of(filled: Set<String>): Map<String, Any> {
        val inPath = pathFields(filled)
        // The operation requires its own names; a renamed field is required when its upstream name is.
        val upstreamRequired = (body["required"] as? List<*>).orEmpty().toSet()
        val required = act.fields.filter { act.upstream(it) in upstreamRequired }.toSet() + inPath + act.inputs.keys
        return mapOf(
            "\$schema" to "https://json-schema.org/draft/2020-12/schema",
            "type" to "object",
            "title" to act.title,
            // An input is the resource file's own field, with its own schema; the rest are the operation's.
            "properties" to (act.inputs + act.fields.associateWith { field ->
                val property = checkNotNull(property(act.upstream(field))) {
                    "$file: ${act.operation} has no field ${act.upstream(field)}"
                }
                // A field the gateway encodes is described as what the client sends: plain text. The upstream's
                // own words ("using Base64 encoding") would have the client encode it, and it be encoded twice.
                act.encode[field]?.let { encoding ->
                    (property as Map<*, *>) + ("description" to "Plain text, as is: the gateway encodes it in " +
                        "$encoding before sending it on.")
                } ?: property
            }).mapValues { (field, property) -> worded(field, property as Map<*, *>) },
            "required" to (act.inputs.keys + act.fields).filter { it in required },
            "additionalProperties" to false,
        ) + alternatives()
    }

    /**
     * [property] as a person reads it in a form: titled, by the resource file's words, else its own title, else
     * from the field's name; described by the resource file's words, else its own.
     */
    private fun worded(field: String, property: Map<*, *>): Map<*, *> =
        property + ("title" to (property["title"] ?: FieldTitle.of(field))) + act.describe[field].orEmpty()

    private fun property(field: String): Any? {
        val branches = listOf("oneOf", "anyOf").flatMap { body[it] as? List<*> ?: emptyList<Any?>() }
            .filterIsInstance<Map<*, *>>()
        return (body["properties"] as? Map<*, *>)?.get(field)
            ?: branches.firstNotNullOfOrNull { (it["properties"] as? Map<*, *>)?.get(field) }
            ?: route.parameters.firstOrNull { it.name == field && it.location == "path" }
                ?.let { it.schema + ("description" to it.description) }
    }

    /**
     * The body's `anyOf` and `oneOf`, kept when each branch is an object that
     * asks only for fields the client fills: GitHub's review request needs
     * reviewers or teams, at least one, and the form says so.
     */
    private fun alternatives(): Map<String, Any> = listOf("anyOf", "oneOf").mapNotNull { keyword ->
        val branches = body[keyword] as? List<*> ?: return@mapNotNull null
        val objects = branches.all { (it as? Map<*, *>)?.get("type").let { type -> type == null || type == "object" } }
        val named = branches.flatMap { ((it as? Map<*, *>)?.get("required") as? List<*>).orEmpty() }
        if (objects && named.all { it in act.fields }) keyword to (branches as Any) else null
    }.toMap()
}
