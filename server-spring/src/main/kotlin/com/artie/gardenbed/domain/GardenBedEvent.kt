package com.artie.gardenbed.domain

import java.time.Instant

/** Derived, never stored -- exists only as a field on GardenBedState,
 *  the fold RESULT, not on anything persisted. */
enum class GardenBedStatus {
    EMPTY,
    PLANTED,
    READY_TO_HARVEST
}

/**
 * The actual source of truth. Every fact this system knows about a
 * bed is one of these, in order. Nothing here is ever mutated or
 * deleted once written -- correcting a mistake means appending a new
 * event that says so, never editing history.
 *
 * `sequenceNo` is 1-based and scoped per bed (bed #7's events are
 * 1, 2, 3... independent of bed #8's own 1, 2, 3...). It's both the
 * ordering key and the optimistic concurrency token -- see EventStore.
 */
sealed interface GardenBedEvent {
    val gardenBedId: Long
    val sequenceNo: Int
    val occurredAt: Instant

    data class SeedSown(
        override val gardenBedId: Long,
        override val sequenceNo: Int,
        val seedType: String,
        val depthCm: Int?,
        override val occurredAt: Instant = Instant.now()
    ) : GardenBedEvent

    data class CellWatered(
        override val gardenBedId: Long,
        override val sequenceNo: Int,
        val milliliters: Int,
        override val occurredAt: Instant = Instant.now()
    ) : GardenBedEvent

    data class BedHarvested(
        override val gardenBedId: Long,
        override val sequenceNo: Int,
        val yieldGrams: Int,
        val cropName: String,
        override val occurredAt: Instant = Instant.now()
    ) : GardenBedEvent
}
