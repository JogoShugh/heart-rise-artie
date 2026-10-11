package com.artie.gardenbed.domain

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
 * An event is only what happened. Which bed, its number in the bed's
 * stream (1-based, per bed; also the optimistic concurrency token) and
 * when it occurred are the event store's record of it (Recorded, in
 * heart-eventsourcing).
 */
sealed interface GardenBedEvent {
    data class SeedSown(val seedType: String, val depthCm: Int?) : GardenBedEvent

    data class CellWatered(val milliliters: Int) : GardenBedEvent

    data class BedHarvested(val yieldGrams: Int, val cropName: String) : GardenBedEvent
}
