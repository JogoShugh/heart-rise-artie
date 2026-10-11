package com.artie.heart

/** What an act left behind: the resource's new state, and any headers the act adds to its answer. */
data class Acted<S>(val state: S, val headers: Map<String, String> = emptyMap())

/** One search result: a resource's id and the title its `item` link carries. */
data class Found(val id: Long, val title: String)

/**
 * A declared resource type, as a server registers it with a binding: the
 * [layers] rendered from its declaration, how one resource is read ([load],
 * which throws [ResourceNotFoundException] for an unknown id), what each act
 * does ([perform], given the act's name and its checked body), and, when the
 * declaration has a [Finding], how resources are found ([find], by part of a
 * name or by id; either may be absent). Every binding serves the rest.
 */
class HeartResource<S, K>(
    val layers: Layers<S, K>,
    val load: suspend (Long) -> S,
    val perform: suspend (id: Long, act: String, input: ActInput) -> Acted<S>,
    val find: (suspend (name: String?, id: Long?) -> List<Found>)? = null,
)
