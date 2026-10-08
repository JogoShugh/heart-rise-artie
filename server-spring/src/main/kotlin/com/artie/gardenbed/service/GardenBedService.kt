package com.artie.gardenbed.service

import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.eventstore.EventStore
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.gardenbed.web.dto.HarvestResult
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.time.Instant

class BedNotFoundException(id: Long) : RuntimeException("No garden bed with id $id")
class InvalidTransitionException(message: String) : RuntimeException(message)

@Service
class GardenBedService(
    private val aggregates: GardenBedAggregateRepository,
    private val eventStore: EventStore
) {

    private suspend fun requireAggregate(id: Long): GardenBedAggregate =
        aggregates.findById(id) ?: throw BedNotFoundException(id)

    /** Beds whose label contains [name], ignoring case, in id order. A null name matches every bed. */
    suspend fun findBeds(name: String?): List<GardenBedAggregate> =
        aggregates.findAll().toList()
            .filter { name == null || it.label.contains(name, ignoreCase = true) }
            .sortedBy { it.id }

    suspend fun currentState(id: Long): GardenBedState {
        val bed = requireAggregate(id)
        return eventStore.currentState(id, bed.label)
    }

    suspend fun stateAt(id: Long, asOf: Instant): GardenBedState {
        val bed = requireAggregate(id)
        return eventStore.stateAt(id, bed.label, asOf)
    }

    suspend fun history(id: Long): List<GardenBedEvent> {
        requireAggregate(id) // 404s if the bed doesn't exist, even with zero events
        return eventStore.fullHistory(id)
    }

    /**
     * Every mutating action follows the same shape: load current
     * (projected) state, validate the transition is legal FROM THAT
     * STATE, build the typed event, hand it to the event store to
     * append -- which is where the REAL concurrency check happens, via
     * the database's unique constraint, not here. Checking status
     * here catches "this action doesn't make sense for this bed right
     * now" (a 409); the event store catches "someone else wrote first"
     * (a 412) -- two different failure modes, deliberately handled in
     * two different places.
     */
    suspend fun sowSeed(id: Long, seedType: String, depthCm: Int?): GardenBedState {
        val bed = requireAggregate(id)
        val current = eventStore.currentState(id, bed.label)
        if (current.status != GardenBedStatus.EMPTY) {
            throw InvalidTransitionException(
                "Cannot sow seed on bed $id: status is ${current.status}, must be EMPTY"
            )
        }
        val event = GardenBedEvent.SeedSown(
            gardenBedId = id,
            sequenceNo = current.lastSequenceNo + 1,
            seedType = seedType,
            depthCm = depthCm
        )
        eventStore.append(event)
        return eventStore.currentState(id, bed.label)
    }

    suspend fun waterCell(id: Long, milliliters: Int): GardenBedState {
        val bed = requireAggregate(id)
        val current = eventStore.currentState(id, bed.label)
        if (current.status != GardenBedStatus.PLANTED) {
            throw InvalidTransitionException(
                "Cannot water bed $id: status is ${current.status}, must be PLANTED"
            )
        }
        val event = GardenBedEvent.CellWatered(
            gardenBedId = id,
            sequenceNo = current.lastSequenceNo + 1,
            milliliters = milliliters
        )
        eventStore.append(event)
        return eventStore.currentState(id, bed.label)
    }

    suspend fun harvest(id: Long): Pair<GardenBedState, HarvestResult> {
        val bed = requireAggregate(id)
        val current = eventStore.currentState(id, bed.label)
        if (current.status != GardenBedStatus.READY_TO_HARVEST) {
            throw InvalidTransitionException(
                "Cannot harvest bed $id: status is ${current.status}, must be READY_TO_HARVEST"
            )
        }
        val yieldGrams = current.growthPoints * 4 // same arbitrary yield formula as before
        val cropName = current.cropName ?: "unknown crop"

        val event = GardenBedEvent.BedHarvested(
            gardenBedId = id,
            sequenceNo = current.lastSequenceNo + 1,
            yieldGrams = yieldGrams,
            cropName = cropName
        )
        eventStore.append(event)
        val updated = eventStore.currentState(id, bed.label)
        return updated to HarvestResult(yieldGrams, cropName)
    }
}
