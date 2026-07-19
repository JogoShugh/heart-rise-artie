package com.artie.gardenbed.domain

import java.time.Instant

/**
 * The RESULT of folding events -- never persisted as-is (except as a
 * disposable cache in garden_bed_snapshot). This is what current state
 * "is": the answer you get from replaying a bed's event log, nothing
 * more.
 */
data class GardenBedState(
    val id: Long,
    val label: String,
    val status: GardenBedStatus = GardenBedStatus.EMPTY,
    val cropName: String? = null,
    val growthPoints: Int = 0,
    val moistureLevel: Int = 0,
    val plantedAt: Instant? = null,
    /** The sequence number of the last event folded into this state --
     *  this IS the concurrency token: "append the next event only if
     *  the bed's last known sequence is still this number." */
    val lastSequenceNo: Int = 0
) {
    companion object {
        const val HARVEST_THRESHOLD = 100

        fun empty(id: Long, label: String): GardenBedState = GardenBedState(id = id, label = label)
    }
}

/**
 * The whole idea in one object: fold a list of events, in order, into
 * a state. Current state is `project(empty, allEvents)`. State as of
 * last Tuesday is `project(empty, allEvents.filter { it.occurredAt <=
 * lastTuesday })`. Same function either way -- point-in-time
 * reconstruction isn't a separate feature, it's just folding a
 * different prefix of the same log.
 */
object GardenBedProjector {

    fun project(seed: GardenBedState, events: List<GardenBedEvent>): GardenBedState =
        events.sortedBy { it.sequenceNo }.fold(seed, ::applyEvent)

    private fun applyEvent(state: GardenBedState, event: GardenBedEvent): GardenBedState = when (event) {
        is GardenBedEvent.SeedSown -> state.copy(
            status = GardenBedStatus.PLANTED,
            cropName = event.seedType,
            growthPoints = 0,
            plantedAt = event.occurredAt,
            lastSequenceNo = event.sequenceNo
        )

        is GardenBedEvent.CellWatered -> {
            val newGrowth = (state.growthPoints + event.milliliters / 5)
                .coerceAtMost(GardenBedState.HARVEST_THRESHOLD)
            val newStatus = if (newGrowth >= GardenBedState.HARVEST_THRESHOLD)
                GardenBedStatus.READY_TO_HARVEST else state.status

            state.copy(
                growthPoints = newGrowth,
                moistureLevel = (state.moistureLevel + event.milliliters / 10).coerceAtMost(100),
                status = newStatus,
                lastSequenceNo = event.sequenceNo
            )
        }

        is GardenBedEvent.BedHarvested -> state.copy(
            status = GardenBedStatus.EMPTY,
            cropName = null,
            growthPoints = 0,
            plantedAt = null,
            lastSequenceNo = event.sequenceNo
        )
    }
}
