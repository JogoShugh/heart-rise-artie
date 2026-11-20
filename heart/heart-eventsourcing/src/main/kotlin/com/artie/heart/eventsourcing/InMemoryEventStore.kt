package com.artie.heart.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An event store in memory, for tests and demos. It keeps events as JSON, as
 * the Postgres store does, so the two answer the same way; and it refuses a
 * number already taken under one lock, as Postgres's UNIQUE constraint does.
 */
class InMemoryEventStore(mapper: ObjectMapper = defaultMapper()) : JsonEventStore(mapper) {
    private val lock = Mutex()
    private val streams = mutableMapOf<Pair<String, Long>, MutableList<StoredEvent>>()
    private val snapshots = mutableMapOf<Pair<String, Long>, StoredSnapshot>()

    override suspend fun insert(stream: String, id: Long, event: StoredEvent) = lock.withLock {
        val events = streams.getOrPut(stream to id) { mutableListOf() }
        if (events.any { it.sequenceNo == event.sequenceNo }) throw taken(stream, id, event.sequenceNo)
        events += event
        events.sortBy { it.sequenceNo }
    }

    override suspend fun events(stream: String, id: Long, after: Int): List<StoredEvent> = lock.withLock {
        streams[stream to id].orEmpty().filter { it.sequenceNo > after }
    }

    override suspend fun lastSequenceNo(stream: String, id: Long): Int? = lock.withLock {
        streams[stream to id]?.lastOrNull()?.sequenceNo
    }

    override suspend fun snapshot(stream: String, id: Long): StoredSnapshot? = lock.withLock { snapshots[stream to id] }

    override suspend fun saveSnapshot(stream: String, id: Long, snapshot: StoredSnapshot) = lock.withLock {
        snapshots[stream to id] = snapshot
    }

    override suspend fun forget(stream: String) = lock.withLock {
        streams.keys.removeIf { it.first == stream }
        snapshots.keys.removeIf { it.first == stream }
        Unit
    }
}
