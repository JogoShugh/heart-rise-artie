package com.artie.heart

import java.time.Instant

/** What an act left behind: the resource's new state, and any headers the act adds to its answer. */
data class Acted<S>(val state: S, val headers: Map<String, String> = emptyMap())

/**
 * One search result: a resource's id, the title its `item` link carries, and
 * the values of its item path's variables; by default, its id.
 */
data class Found(val id: Long, val title: String, val variables: PathVariables = mapOf("id" to "$id"))

/** One thing that happened to a resource: its number in the resource's history, its type, when, and its data. */
data class PastEvent(val sequenceNo: Int, val type: String, val occurredAt: Instant, val event: Any)

/**
 * A resource's past, for a type whose state is folded from events: its
 * state [at] any instant, and its [history] in order. Both throw
 * [ResourceNotFoundException] for an unknown id.
 */
class Past<S>(
    val at: suspend (id: Long, instant: Instant) -> S,
    val history: suspend (id: Long) -> List<PastEvent>,
)

/**
 * A declared resource type, as a server registers it with a binding: the
 * [layers] rendered from its declaration, how one resource is read ([load],
 * which throws [ResourceNotFoundException] for an unknown id), what each act
 * does ([perform], given the checked state, the act's name and its checked
 * body), and, when the
 * declaration has a [Finding], how resources are found ([find], by part of a
 * name or by id; either may be absent), when the type keeps one, its
 * [past], and, when the bookmark offers acts for the type, what each does
 * ([performAtBookmark], given the act's name and its checked body; it
 * returns the state of the resource the act brought into being). Every
 * binding serves the rest.
 *
 * [perform] is handed the state the binding read and checked the client's
 * validator against (SPEC §6.2), and acts on that state, never on one read
 * again: it records the act only if the resource is still at that state, and
 * throws [ConcurrentWriteException] otherwise, which is a stale refusal. So a
 * write that lands between the check and the act is caught.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class HeartResource<S, K>(
    val layers: Layers<S, K>,
    val load: suspend (Long) -> S = { throw ResourceNotFoundException("${layers.type.collection} has no ids") },
    val perform: suspend (current: S, act: String, input: ActInput) -> Acted<S>,
    val find: (suspend (name: String?, id: Long?) -> List<Found>)? = null,
    val past: Past<S>? = null,
    val performAtBookmark: (suspend (act: String, input: ActInput) -> Acted<S>)? = null,
    /** How a resource of a type that declares its item path is read: by the path's variables. */
    val loadAt: (suspend (PathVariables) -> S)? = null,
    /** How a type that declares its collection path is searched: by its search form's fields, as filled. */
    val search: (suspend (SearchValues) -> List<Found>)? = null,
) {
    private val type = layers.type

    init {
        val declared = type.bookmarkActs.isNotEmpty()
        require(declared == (performAtBookmark != null)) {
            "${type.collection}: bookmark acts need performAtBookmark, and only they do"
        }
        val itemPath = type.state.itemPath != null
        require(itemPath == (loadAt != null)) {
            "${type.collection}: a declared item path needs loadAt, and only it does"
        }
        // find and past name resources by id; a type with a declared item path has no id to name them by.
        require(!itemPath || (find == null && past == null)) { "${type.collection}: an item path has no find or past" }
        val collectionPath = type.finding?.collectionPath != null
        require(collectionPath == (search != null)) {
            "${type.collection}: a declared collection path needs search, and only it does"
        }
    }

    /**
     * A search as a request asked for it: the collection path's [variables]
     * and the [query]. A type that declares its collection path is searched by
     * those and its filters; any other, by part of a name and by id.
     */
    suspend fun searched(variables: PathVariables, query: (String) -> String?): Pair<Search, List<Found>> {
        val finding = checkNotNull(type.finding)
        val search = search ?: return byName(query)
        val filters = finding.filters(type.itemPath).mapNotNull { field -> query(field.name)?.let { field.name to it } }
        val path = ItemPath.fill(checkNotNull(finding.collectionPath), variables)
        val found = search(variables + filters)
        return Search(type.collection, path = path, filters = filters.toMap(), item = type.itemPath.template) to found
    }

    private suspend fun byName(query: (String) -> String?): Pair<Search, List<Found>> {
        val name = query("name")
        val id = query("id")?.toLongOrNull()
        return Search(type.collection, name, id) to checkNotNull(find)(name, id)
    }

    /** The resource whose item path a request filled with [variables]; [ResourceNotFoundException] when none. */
    suspend fun locate(variables: PathVariables): S =
        loadAt?.invoke(variables) ?: load(id(variables))

    /** The id a request to `/{collection}/{id}` names; [ResourceNotFoundException] when it is no number. */
    fun id(variables: PathVariables): Long {
        val id = variables["id"]
        return id?.toLongOrNull() ?: throw ResourceNotFoundException("no ${type.collection} $id")
    }

    /** Where [state] lives: its item path, filled from it. */
    fun location(state: S): String = type.itemPath.expand(type.state.bodyOf(state))
}

/** An item path's variables, by name, as a request's path filled them: `owner` to `acme`. */
typealias PathVariables = Map<String, String>

/** A search form's fields, by name, as a request filled them: the collection path's variables and the filters. */
typealias SearchValues = Map<String, String>
