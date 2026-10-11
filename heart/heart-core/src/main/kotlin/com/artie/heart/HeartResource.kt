package com.artie.heart

import java.time.Instant

/** What an act left behind: the resource's new state, and any headers the act adds to its answer. */
data class Acted<S>(val state: S, val headers: Map<String, String> = emptyMap())

/** One search result: a resource's id and the title its `item` link carries. */
data class Found(val id: Long, val title: String)

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
class HeartResource<S, K>(
    val layers: Layers<S, K>,
    val load: suspend (Long) -> S,
    val perform: suspend (current: S, act: String, input: ActInput) -> Acted<S>,
    val find: (suspend (name: String?, id: Long?) -> List<Found>)? = null,
    val past: Past<S>? = null,
    val performAtBookmark: (suspend (act: String, input: ActInput) -> Acted<S>)? = null,
) {
    init {
        val declared = layers.type.bookmarkActs.isNotEmpty()
        require(declared == (performAtBookmark != null)) {
            "${layers.type.collection}: bookmark acts need performAtBookmark, and only they do"
        }
    }
}
