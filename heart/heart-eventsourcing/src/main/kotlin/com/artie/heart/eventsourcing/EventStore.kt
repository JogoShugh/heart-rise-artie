package com.artie.heart.eventsourcing

import com.artie.heart.ConcurrentWriteException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * The events of every event-sourced resource, one stream per resource.
 *
 * [append] stores an event at the number the writer expects to be next. A
 * number already taken means another writer won: a [ConcurrentWriteException],
 * which heart-core turns into a stale refusal. So optimistic concurrency is
 * enforced by the store itself, not by a check and a write with a gap between.
 */
interface EventStore {
    suspend fun <E : Any, S : Any> append(
        spec: EventSourcing<E, S>,
        id: Long,
        sequenceNo: Int,
        event: E,
        occurredAt: Instant = Instant.now(),
    ): Recorded<E>

    /** The number the next event of resource [id] takes. */
    suspend fun nextSequenceNo(spec: EventSourcing<*, *>, id: Long): Int

    /**
     * The state of resource [id]: now, or as of [asOf] (only the events that
     * occurred by then). A snapshot is used when it can be: never one taken
     * after [asOf], and never when [fromSnapshot] is false.
     */
    suspend fun <E : Any, S : Any> state(
        spec: EventSourcing<E, S>,
        id: Long,
        asOf: Instant? = null,
        fromSnapshot: Boolean = true,
    ): S

    /** Every event of resource [id], in order. */
    suspend fun <E : Any, S : Any> history(spec: EventSourcing<E, S>, id: Long): List<Recorded<E>>

    /** Removes every event and snapshot of [stream]: for resetting a test or a demo. */
    suspend fun forget(stream: String)
}

/** An event as a store keeps it: its number, its type name, its JSON, and when it occurred. */
class StoredEvent(val sequenceNo: Int, val type: String, val payload: String, val occurredAt: Instant)

/** A snapshot as a store keeps it: the state's JSON after event [sequenceNo], and when it was taken. */
class StoredSnapshot(val sequenceNo: Int, val state: String, val takenAt: Instant)

/**
 * The folding, the JSON and the snapshots, shared by every store, so every
 * store answers the same way. A store supplies only how rows are kept.
 */
abstract class JsonEventStore(protected val mapper: ObjectMapper = defaultMapper()) : EventStore {

    /** Stores [event]; throws [ConcurrentWriteException] when its number is taken. */
    protected abstract suspend fun insert(stream: String, id: Long, event: StoredEvent)

    protected abstract suspend fun events(stream: String, id: Long, after: Int): List<StoredEvent>

    protected abstract suspend fun lastSequenceNo(stream: String, id: Long): Int?

    protected abstract suspend fun snapshot(stream: String, id: Long): StoredSnapshot?

    protected abstract suspend fun saveSnapshot(stream: String, id: Long, snapshot: StoredSnapshot)

    override suspend fun <E : Any, S : Any> append(
        spec: EventSourcing<E, S>,
        id: Long,
        sequenceNo: Int,
        event: E,
        occurredAt: Instant,
    ): Recorded<E> {
        val stored = StoredEvent(sequenceNo, spec.typeOf(event), mapper.writeValueAsString(event), occurredAt)
        insert(spec.stream, id, stored)
        // The event is stored. A snapshot is a disposable shortcut, so failing to
        // take one must not turn a write that happened into an error.
        if (sequenceNo % spec.snapshotEvery == 0) {
            runCatching { takeSnapshot(spec, id) }
                .onFailure { log.warn("snapshot of ${spec.stream} $id skipped; state still folds from events", it) }
        }
        return Recorded(id, sequenceNo, occurredAt, event)
    }

    override suspend fun nextSequenceNo(spec: EventSourcing<*, *>, id: Long): Int =
        (lastSequenceNo(spec.stream, id) ?: 0) + 1

    override suspend fun <E : Any, S : Any> state(
        spec: EventSourcing<E, S>,
        id: Long,
        asOf: Instant?,
        fromSnapshot: Boolean,
    ): S {
        // A snapshot taken after asOf already holds later events, so it is not used then.
        val snapshot = snapshot(spec.stream, id)
            ?.takeIf { fromSnapshot && (asOf == null || !it.takenAt.isAfter(asOf)) }
        val seed = snapshot?.let { mapper.readValue(it.state, spec.stateClass) } ?: spec.empty(id)
        val tail = events(spec.stream, id, after = snapshot?.sequenceNo ?: 0)
            .filter { asOf == null || !it.occurredAt.isAfter(asOf) }
            .map { decode(mapper, spec, id, it) }
        return spec.fold(seed, tail)
    }

    override suspend fun <E : Any, S : Any> history(spec: EventSourcing<E, S>, id: Long): List<Recorded<E>> =
        events(spec.stream, id, after = 0).map { decode(mapper, spec, id, it) }

    private suspend fun <E : Any, S : Any> takeSnapshot(spec: EventSourcing<E, S>, id: Long) {
        val all = history(spec, id)
        val state = mapper.writeValueAsString(spec.fold(spec.empty(id), all))
        saveSnapshot(spec.stream, id, StoredSnapshot(all.last().sequenceNo, state, Instant.now()))
    }

    companion object {
        private val log = LoggerFactory.getLogger(JsonEventStore::class.java)

        /** Kotlin classes and java.time, as events and states use them. */
        fun defaultMapper(): ObjectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}

/** [stored] as the event it records, by the class its type name stands for. */
private fun <E : Any> decode(
    mapper: ObjectMapper,
    spec: EventSourcing<E, *>,
    id: Long,
    stored: StoredEvent,
): Recorded<E> {
    val type = checkNotNull(spec.types[stored.type]) { "stream ${spec.stream} has no event type ${stored.type}" }
    return Recorded(id, stored.sequenceNo, stored.occurredAt, mapper.readValue(stored.payload, type))
}

/** A number already taken in a resource's stream means another writer won. */
internal fun taken(stream: String, id: Long, sequenceNo: Int): ConcurrentWriteException =
    ConcurrentWriteException(
        "$stream $id: event $sequenceNo was already taken by a concurrent write. Re-read the state and retry.",
    )
