package com.artie.gardenbed.service

import com.artie.gardenbed.declaration.dto.HarvestResult
import com.artie.gardenbed.domain.GARDEN_BEDS
import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.time.Instant

/** 404, as heart-core names it, so every binding answers it the same way. */
class BedNotFoundException(id: Long) : ResourceNotFoundException("No garden bed with id $id")

@Service
class GardenBedService(
    private val aggregates: GardenBedAggregateRepository,
    private val events: EventStore,
) {

    private suspend fun requireAggregate(id: Long): GardenBedAggregate =
        aggregates.findById(id) ?: throw BedNotFoundException(id)

    private suspend fun state(bed: GardenBedAggregate, asOf: Instant? = null): GardenBedState =
        events.state(GARDEN_BEDS, checkNotNull(bed.id), asOf).copy(label = bed.label)

    /** Beds whose label contains [name], ignoring case, and whose id is [id], in id order. Null matches all. */
    suspend fun findBeds(name: String?, id: Long? = null): List<GardenBedAggregate> =
        aggregates.findAll().toList()
            .filter { name == null || it.label.contains(name, ignoreCase = true) }
            .filter { id == null || it.id == id }
            .sortedBy { it.id }

    suspend fun currentState(id: Long): GardenBedState = state(requireAggregate(id))

    suspend fun stateAt(id: Long, asOf: Instant): GardenBedState = state(requireAggregate(id), asOf)

    /** 404s if the bed doesn't exist, even with zero events. */
    suspend fun history(id: Long): List<Recorded<GardenBedEvent>> {
        requireAggregate(id)
        return events.history(GARDEN_BEDS, id)
    }

    /**
     * Every act has the same shape. It is given [bed], the state the client's
     * ETag was checked against, which the HEART library has already found
     * offers this act. The act builds its event from that state and appends
     * it at the number after it. If another write took that number first,
     * the bed has moved on, and the event store refuses it: a stale act, 412.
     */
    suspend fun sowSeed(bed: GardenBedState, seedType: String, depthCm: Int?): GardenBedState =
        act(bed) { GardenBedEvent.SeedSown(seedType, depthCm) }

    suspend fun waterCell(bed: GardenBedState, milliliters: Int): GardenBedState =
        act(bed) { GardenBedEvent.CellWatered(milliliters) }

    suspend fun harvest(bed: GardenBedState): Pair<GardenBedState, HarvestResult> {
        val result = HarvestResult(bed.growthPoints * YIELD_PER_POINT, bed.cropName ?: "unknown crop")
        return act(bed) { GardenBedEvent.BedHarvested(result.yieldGrams, result.cropName) } to result
    }

    private suspend fun act(bed: GardenBedState, event: (GardenBedState) -> GardenBedEvent): GardenBedState {
        events.append(GARDEN_BEDS, bed.id, bed.lastSequenceNo + 1, event(bed))
        return currentState(bed.id)
    }

    private companion object {
        /** The same arbitrary yield formula as before. */
        const val YIELD_PER_POINT = 4
    }
}
