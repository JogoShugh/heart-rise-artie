package com.artie.gardenbed.bdd

import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedProjector
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.eventstore.ConcurrentAppendException
import com.artie.gardenbed.eventstore.EventStore
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.gardenbed.service.GardenBedService
import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Instant

/**
 * The scenarios run the real controllers and the real GardenBedService
 * against an in-memory event log, so no Postgres is needed. Only storage
 * is replaced: legality checks, projection, and the sequence-number
 * concurrency rule are the production code paths.
 */
class InMemoryEventStore : EventStore(mock(), mock(), mock(), ObjectMapper()) {
    private val log = mutableMapOf<Long, MutableList<GardenBedEvent>>()

    fun reset() = log.clear()

    override suspend fun nextSequenceNo(bedId: Long): Int =
        (log[bedId]?.maxOfOrNull { it.sequenceNo } ?: 0) + 1

    /** Same rule as the database's UNIQUE(garden_bed_id, sequence_no). */
    override suspend fun append(event: GardenBedEvent) {
        val events = log.getOrPut(event.gardenBedId) { mutableListOf() }
        if (events.any { it.sequenceNo == event.sequenceNo }) {
            throw ConcurrentAppendException("Bed ${event.gardenBedId}: sequence ${event.sequenceNo} was already taken")
        }
        events += event
    }

    override suspend fun currentState(bedId: Long, label: String): GardenBedState =
        GardenBedProjector.project(GardenBedState.empty(bedId, label), log[bedId].orEmpty())

    override suspend fun stateAt(bedId: Long, label: String, asOf: Instant): GardenBedState =
        GardenBedProjector.project(
            GardenBedState.empty(bedId, label),
            log[bedId].orEmpty().filter { !it.occurredAt.isAfter(asOf) },
        )

    override suspend fun fullHistory(bedId: Long): List<GardenBedEvent> = log[bedId].orEmpty().toList()
}

/** Bed identities. A suspend function on a Mockito mock may return its
 *  value directly, so the answer below needs no coroutine machinery. */
class InMemoryBeds {
    private val labels = mutableMapOf<Long, String>()

    fun reset() = labels.clear()

    fun add(id: Long) {
        labels[id] = "Bed $id"
    }

    fun label(id: Long): String = labels.getValue(id)

    val repository: GardenBedAggregateRepository = mock(GardenBedAggregateRepository::class.java) { invocation ->
        if (invocation.method.name == "findById") {
            val id = invocation.arguments[0] as Long
            labels[id]?.let { GardenBedAggregate(id = id, label = it) }
        } else {
            null
        }
    }
}

@TestConfiguration
class InMemoryGardenBedsConfig {
    @Bean
    fun inMemoryBeds() = InMemoryBeds()

    @Bean
    fun inMemoryEventStore() = InMemoryEventStore()

    @Bean
    fun gardenBedService(beds: InMemoryBeds, store: InMemoryEventStore) = GardenBedService(beds.repository, store)
}
