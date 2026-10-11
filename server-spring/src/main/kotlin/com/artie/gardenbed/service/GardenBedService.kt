package com.artie.gardenbed.service

import com.artie.gardenbed.declaration.dto.HarvestResult
import com.artie.gardenbed.domain.GARDEN_BEDS
import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.heart.NotOfferedException
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.time.Instant

/** 404, as heart-core names it, so every binding answers it the same way. */
class BedNotFoundException(id: Long) : ResourceNotFoundException("No garden bed with id $id")

/** 409: the domain's own guard, refused as heart-core's not-offered. [rel] is the act tried, when known. */
class InvalidTransitionException(message: String, rel: String? = null) : NotOfferedException(message, rel)

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
     * Every mutating action follows the same shape: load current
     * (projected) state, validate the transition is legal FROM THAT
     * STATE, and append the event at the next number -- which is where
     * the REAL concurrency check happens, in the event store, not here.
     * Checking status here catches "this action doesn't make sense for
     * this bed right now" (a 409); the event store catches "someone else
     * wrote first" (a 412) -- two different failure modes, deliberately
     * handled in two different places.
     */
    suspend fun sowSeed(id: Long, seedType: String, depthCm: Int?): GardenBedState =
        act(id, GardenBedStatus.EMPTY, "sow seed on") { GardenBedEvent.SeedSown(seedType, depthCm) }

    suspend fun waterCell(id: Long, milliliters: Int): GardenBedState =
        act(id, GardenBedStatus.PLANTED, "water") { GardenBedEvent.CellWatered(milliliters) }

    suspend fun harvest(id: Long): Pair<GardenBedState, HarvestResult> {
        var result: HarvestResult? = null
        val updated = act(id, GardenBedStatus.READY_TO_HARVEST, "harvest") { current ->
            val yieldGrams = current.growthPoints * YIELD_PER_POINT
            val cropName = current.cropName ?: "unknown crop"
            result = HarvestResult(yieldGrams, cropName)
            GardenBedEvent.BedHarvested(yieldGrams, cropName)
        }
        return updated to checkNotNull(result)
    }

    private suspend fun act(
        id: Long,
        requires: GardenBedStatus,
        verb: String,
        event: (GardenBedState) -> GardenBedEvent,
    ): GardenBedState {
        val bed = requireAggregate(id)
        val current = state(bed)
        if (current.status != requires) {
            throw InvalidTransitionException("Cannot $verb bed $id: status is ${current.status}, must be $requires")
        }
        events.append(GARDEN_BEDS, id, current.lastSequenceNo + 1, event(current))
        return state(bed)
    }

    private companion object {
        /** The same arbitrary yield formula as before. */
        const val YIELD_PER_POINT = 4
    }
}
