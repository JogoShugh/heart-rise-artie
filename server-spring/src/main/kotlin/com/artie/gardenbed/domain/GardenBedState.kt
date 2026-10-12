package com.artie.gardenbed.domain

import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * The RESULT of folding events -- never persisted as-is (except as a
 * disposable cache in heart_snapshot). This is what current state
 * "is": the answer you get from replaying a bed's event log, nothing
 * more.
 */
data class GardenBedState(
    val id: Long,
    val label: String,
    val widthFt: Int = 0,
    val lengthFt: Int = 0,
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
    /** The most water the bed takes now: each millilitre adds a tenth to moisture, which tops out at [MAX_MOISTURE]. */
    fun waterRoom(): Int = (MAX_MOISTURE - moistureLevel) * ML_PER_MOISTURE

    companion object {
        const val HARVEST_THRESHOLD = 100
        const val MAX_MOISTURE = 100
        const val ML_PER_MOISTURE = 10

        fun empty(id: Long, label: String): GardenBedState = GardenBedState(id = id, label = label)
    }
}

/**
 * The whole idea in one object: fold a bed's events, in order, into a state.
 * Current state is the fold of every event. State as of last Tuesday is the
 * fold of the events up to last Tuesday. Same function either way -- point
 * in time reconstruction isn't a separate feature, it's just folding a
 * different prefix of the same log. Each event comes as the store recorded
 * it: its number in the bed's stream and when it occurred.
 */
object GardenBedProjector {

    fun apply(state: GardenBedState, recorded: Recorded<GardenBedEvent>): GardenBedState =
        when (val event = recorded.event) {
            is GardenBedEvent.SeedSown -> state.copy(
                status = GardenBedStatus.PLANTED,
                cropName = event.seedType,
                growthPoints = 0,
                plantedAt = recorded.occurredAt,
            )

            is GardenBedEvent.CellWatered -> {
                val newGrowth = (state.growthPoints + event.milliliters / 5)
                    .coerceAtMost(GardenBedState.HARVEST_THRESHOLD)
                val newStatus = if (newGrowth >= GardenBedState.HARVEST_THRESHOLD)
                    GardenBedStatus.READY_TO_HARVEST else state.status

                state.copy(
                    growthPoints = newGrowth,
                    moistureLevel = state.moistureLevel + event.milliliters / GardenBedState.ML_PER_MOISTURE,
                    status = newStatus,
                )
            }

            is GardenBedEvent.BedHarvested -> state.copy(
                status = GardenBedStatus.EMPTY,
                cropName = null,
                growthPoints = 0,
                // The crop has drunk the soil's water (decided 2026-10-12).
                moistureLevel = 0,
                plantedAt = null,
            )
        }.copy(lastSequenceNo = recorded.sequenceNo)
}
