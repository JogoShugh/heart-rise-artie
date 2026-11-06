package com.artie.gardenbed.bdd

import com.artie.gardenbed.domain.GARDEN_BEDS
import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.gardenbed.service.GardenBedService
import com.artie.heart.eventsourcing.InMemoryEventStore
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * The scenarios run the real routes and the real GardenBedService against
 * the HEART library's in-memory event store, so no Postgres is needed. Only
 * storage is replaced: legality checks, projection, and the sequence-number
 * concurrency rule are the production code paths. This gives steps a bed's
 * events directly, for setting up a state or writing as someone else.
 */
class BedEventLog {
    val store = InMemoryEventStore()

    fun reset() = runBlocking { store.forget(GARDEN_BEDS.stream) }

    /** Appends [event] at bed [id]'s next number, as any writer would. */
    suspend fun append(id: Long, event: GardenBedEvent) {
        store.append(GARDEN_BEDS, id, store.nextSequenceNo(GARDEN_BEDS, id), event)
    }
}

/** Bed identities, names and sizes. A suspend function on a Mockito mock may
 *  return its value directly, so the answers below need no coroutine machinery. */
class InMemoryBeds {
    private val beds = mutableMapOf<Long, GardenBedAggregate>()

    fun reset() = beds.clear()

    fun add(id: Long, label: String = "Bed $id") {
        beds[id] = GardenBedAggregate(id = id, label = label, widthFt = WIDTH_FT, lengthFt = LENGTH_FT)
    }

    fun label(id: Long): String = beds.getValue(id).label

    val repository: GardenBedAggregateRepository = mock(GardenBedAggregateRepository::class.java) { invocation ->
        when (invocation.method.name) {
            "findById" -> beds[invocation.arguments[0] as Long]
            "findAll" -> beds.values.sortedBy { it.id }.asFlow()
            "save" -> (invocation.arguments[0] as GardenBedAggregate)
                .copy(id = (beds.keys.maxOrNull() ?: 0) + 1)
                .also { beds[checkNotNull(it.id)] = it }
            else -> null
        }
    }

    private companion object {
        const val WIDTH_FT = 4
        const val LENGTH_FT = 8
    }
}

@TestConfiguration
class InMemoryGardenBedsConfig {
    @Bean
    fun inMemoryBeds() = InMemoryBeds()

    @Bean
    fun bedEventLog() = BedEventLog()

    @Bean
    fun gardenBedService(beds: InMemoryBeds, events: BedEventLog) = GardenBedService(beds.repository, events.store)
}
