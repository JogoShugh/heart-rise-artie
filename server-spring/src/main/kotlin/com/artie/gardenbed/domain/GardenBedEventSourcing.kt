package com.artie.gardenbed.domain

import com.artie.heart.eventsourcing.EventSourcing

/**
 * How a garden bed is event-sourced (heart-eventsourcing): its stream, its
 * event types by the names stored with them, and the fold. A bed's label is
 * not an event: it lives with the bed's identity, and is set on the folded
 * state when a bed is read.
 */
val GARDEN_BEDS: EventSourcing<GardenBedEvent, GardenBedState> = EventSourcing(
    stream = "garden-bed",
    types = mapOf(
        "SeedSown" to GardenBedEvent.SeedSown::class.java,
        "CellWatered" to GardenBedEvent.CellWatered::class.java,
        "BedHarvested" to GardenBedEvent.BedHarvested::class.java,
    ),
    stateClass = GardenBedState::class.java,
    empty = { id -> GardenBedState.empty(id, label = "") },
    apply = GardenBedProjector::apply,
)
