package com.artie.gardenbed.service

import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import org.springframework.stereotype.Service

/**
 * The weather over the garden. It is not a client's act, so no form offers
 * it and no description lists it: the garden's operator reports it.
 */
@Service
class GardenWeather(private val beds: GardenBedService) {
    /** A hard frost: every planted bed freezes; empty and ready beds do not. Returns the beds it froze. */
    suspend fun frost(): List<GardenBedState> =
        beds.findBeds(null).map { beds.currentState(checkNotNull(it.id)) }
            .filter { it.status == GardenBedStatus.PLANTED }
            .map { bed -> beds.act(bed) { GardenBedEvent.BedFrozen(bed.cropName ?: "unknown crop") } }
}
