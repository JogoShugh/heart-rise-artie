package com.artie.heart.eventsourcing

import java.time.Instant

/** One stored event: which resource it belongs to, its number in that resource's stream, when, and what. */
data class Recorded<E>(val streamId: Long, val sequenceNo: Int, val occurredAt: Instant, val event: E)

/**
 * How a resource type is event-sourced: the name of its [stream], its event
 * classes by the type name stored with each event, its state class, the
 * state of a resource with no events, and how one event changes a state.
 * State is the fold of a resource's events in order; the number of the last
 * one is its version. A snapshot is kept every [snapshotEvery] events, as a
 * shortcut that never changes the answer.
 */
class EventSourcing<E : Any, S : Any>(
    val stream: String,
    val types: Map<String, Class<out E>>,
    val stateClass: Class<S>,
    val empty: (id: Long) -> S,
    val apply: (S, Recorded<E>) -> S,
    val snapshotEvery: Int = DEFAULT_SNAPSHOT_EVERY,
) {
    /** The type name stored with [event]. */
    fun typeOf(event: E): String =
        types.entries.firstOrNull { it.value.isInstance(event) }?.key
            ?: throw IllegalArgumentException("no type name for ${event::class} in stream $stream")

    /** The state after [events], in order, starting from [seed]. */
    fun fold(seed: S, events: List<Recorded<E>>): S = events.fold(seed, apply)

    companion object {
        const val DEFAULT_SNAPSHOT_EVERY = 5
    }
}
