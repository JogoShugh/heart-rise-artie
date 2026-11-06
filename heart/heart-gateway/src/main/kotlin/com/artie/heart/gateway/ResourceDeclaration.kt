package com.artie.heart.gateway

import com.artie.heart.StatePointer
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode

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
    /** A listed item for which every one of these holds is not one of these resources: GitHub lists pulls as issues. */
    val skip: List<Condition> = emptyList(),
    /**
     * Where the item's own URL is, when its state names its parts only there, as an issue's `/url` does:
     * the read's path variables are read from it, and kept in the state as `hr:address`.
     */
    val address: JsonPointer? = null,
    /** Other list operations whose members are these resources, each served at its own path: an issue's sub-issues. */
    val lists: List<OperationId> = emptyList(),
    /** List operations under one of these resources that another resource file searches by: an issue's comments. */
    val links: List<OperationId> = emptyList(),
    /** Nested objects these resources embed, when they differ from the connector's: `head/repo`. */
    val embed: List<String>? = null,
    /**
     * Where a listed item names a variable of the read's path, when the item has no address of its own;
     * the list's own path names the rest. GitHub's listed branches: `branch: /name`.
     */
    val listed: Map<String, StatePointer> = emptyMap(),
    /**
     * Members the upstream changes by itself, with no write: a pull request's mergeability. Left out when the
     * state is compared just before an act; the act must instead still be offered.
     */
    val volatile: List<JsonPointer> = emptyList(),
    /** Filters the search's own operation lacks, each served by another list operation: GitHub's pulls by label. */
    val searchedBy: List<SearchedBy> = emptyList(),
    /**
     * The connector's own words on its search, after the upstream's: what the gateway's search does that the
     * upstream's description doesn't say. GitHub's issue list has pull requests in it; the gateway's leaves them out.
     */
    val searchNote: String? = null,
) {
    /** The operations the file names, checked against the description at start. */
    val named: Set<OperationId> = setOfNotNull(read, search) + acts.map { it.operation } +
        facts.values.map { it.operation } + lists + links + searchedBy.map { it.operation }

    /** [state] without its [volatile] members, the members the upstream changes by itself. */
    fun steady(state: JsonNode): JsonNode = state.deepCopy<JsonNode>().also { copy ->
        volatile.forEach { pointer ->
            (copy.at(pointer.head()) as? ObjectNode)?.remove(pointer.last().matchingProperty)
        }
    }

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
                        fact.path("pointers").fields().asSequence().associate { (variable, at) ->
                            val text = at.asText()
                            // A value of several members, as GitHub's compare takes BASE...HEAD, is a template.
                            FactDeclaration.MEMBER.findAll(text).forEach { pointer(label, it.groupValues[1]) }
                            variable to if ('{' in text) text else pointer(label, text).toString()
                        },
                    )
                },
                skip = Condition.all(node.path("skip")),
                address = node.path("address").asText(null)?.let { pointer(label, it) },
                lists = node.path("lists").map { it.asText() },
                links = node.path("links").map { it.asText() },
                embed = node.path("embed").takeIf { it.isArray }?.map { it.asText() },
                listed = node.path("listed").properties().associate { (variable, at) ->
                    variable to pointer(label, at.asText()).toString()
                },
                volatile = node.path("volatile").map { pointer(label, it.asText()) },
                searchNote = node.path("search-note").asText(null),
                searchedBy = node.path("searched-by").properties().map { (filter, by) ->
                    fun needs(key: String) =
                        checkNotNull(by.path(key).asText(null)) { "$label: searched-by $filter has no $key" }
                    val keep = Condition.all(by.path("keep"))
                    SearchedBy(filter, needs("operation"), keep, pointer(label, needs("address")))
                },
            )
        }

        private fun pointer(label: String, text: String): JsonPointer =
            runCatching { JsonPointer.compile(text) }.getOrElse { error("$label: $text is not a JSON Pointer") }
    }
}

/**
 * A search [filter] the search's own operation lacks, served by another list [operation]: of what it lists, only the
 * items for which every one of [keep] holds are these resources, each at its own URL, found at [address]. GitHub's
 * pull request list has no label filter; its issue list has, and lists pull requests as issues too.
 */
class SearchedBy(val filter: String, val operation: OperationId, val keep: List<Condition>, val address: JsonPointer)

/**
 * Another answer a resource's rules read: the [operation] that gives it, its
 * path filled from the resource's state by [pointers]. GitHub says whether
 * the caller may push not on a pull request but on its repository. A pointer
 * may be a template of several, each in braces: `{/base/ref}...{/head/sha}`.
 */
class FactDeclaration(val operation: OperationId, val pointers: Map<String, StatePointer>) {

    /** Each of the path's variables, as [state] fills it. */
    fun values(state: JsonNode): Map<String, String> = pointers.mapValues { (_, at) ->
        if ('{' in at) MEMBER.replace(at) { state.at(it.groupValues[1]).asText() } else state.at(at).asText()
    }

    companion object {
        /** A member of the state, named by its pointer in braces, in a template. */
        val MEMBER = Regex("""\{(/[^}]*)\}""")
    }
}
