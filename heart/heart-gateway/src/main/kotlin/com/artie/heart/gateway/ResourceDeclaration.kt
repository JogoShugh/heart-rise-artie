package com.artie.heart.gateway

import com.artie.heart.StatePointer
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode

/**
 * One resource of an upstream whose paths aren't HEART's, from its
 * `<name>.resource.yaml`: the operation that [read]s one, the one that lists
 * them ([search]), where in its state each variable of the read's path is
 * ([pointers]), which of the list's query parameters a search form offers
 * ([filters]), where a listed item's [title] is, the [acts] made of the
 * upstream's operations, and the [facts] its reader's rules may read.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class ResourceDeclaration(
    val file: String,
    val noun: String,
    val collection: String,
    val read: OperationId,
    val search: OperationId?,
    val pointers: Map<String, StatePointer>,
    val filters: List<String>,
    val title: JsonPointer?,
    val acts: List<ActDeclaration> = emptyList(),
    val facts: Map<String, FactDeclaration> = emptyMap(),
) {
    /** The operations the file names, checked against the description at start. */
    val named: Set<OperationId> = setOfNotNull(read, search) + acts.map { it.operation } +
        facts.values.map { it.operation }

    companion object {
        /** The declaration in [node], from the file [label], or a refusal naming it. */
        fun of(label: String, node: JsonNode): ResourceDeclaration {
            fun text(name: String): String = checkNotNull(node.path(name).asText(null)) { "$label: no $name" }
            val pointers = node.path("pointers").fields().asSequence().associate { (variable, at) ->
                variable to pointer(label, at.asText()).toString()
            }
            return ResourceDeclaration(
                file = label,
                noun = text("noun"),
                collection = text("collection"),
                read = text("read"),
                search = node.path("search").asText(null),
                pointers = pointers,
                filters = node.path("filters").map { it.asText() },
                title = node.path("title").asText(null)?.let { pointer(label, it) },
                acts = node.path("acts").fields().asSequence()
                    .map { (name, act) -> ActDeclaration.of(label, name, act) }.toList(),
                facts = node.path("facts").fields().asSequence().associate { (name, fact) ->
                    name to FactDeclaration(
                        checkNotNull(fact.path("operation").asText(null)) { "$label: fact $name has no operation" },
                        fact.path("pointers").fields().asSequence()
                            .associate { (variable, at) -> variable to pointer(label, at.asText()).toString() },
                    )
                },
            )
        }

        private fun pointer(label: String, text: String): JsonPointer =
            runCatching { JsonPointer.compile(text) }.getOrElse { error("$label: $text is not a JSON Pointer") }
    }
}

/**
 * Another answer a resource's rules read: the [operation] that gives it, its
 * path filled from the resource's state by [pointers]. GitHub says whether
 * the caller may push not on a pull request but on its repository.
 */
class FactDeclaration(val operation: OperationId, val pointers: Map<String, StatePointer>)
