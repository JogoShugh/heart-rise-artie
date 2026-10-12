package com.artie.gardenbed.declaration

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.service.GardenBedService
import com.artie.gardenbed.declaration.dto.BuildBedRequest
import com.artie.gardenbed.declaration.dto.HarvestResult
import com.artie.gardenbed.declaration.dto.SowSeedRequest
import com.artie.gardenbed.declaration.dto.WaterCellRequest
import com.artie.heart.ActInput
import org.springframework.stereotype.Component

/** What an act left behind: the bed's new state and, for a harvest, what was harvested. */
data class ActResult(val state: GardenBedState, val harvest: HarvestResult? = null)

/**
 * What each act does to a bed. The HEART library runs the checks of SPEC
 * §6.2 before it, the same way over HTTP and CoAP, and hands over a body
 * that satisfies the act's schema.
 */
@Component
class BedActs(private val service: GardenBedService) {

    /** The bookmark's act: a new, empty bed. */
    suspend fun build(input: ActInput): GardenBedState =
        input.bind(BuildBedRequest::class.java).let { service.buildBed(it.label, it.widthFt, it.lengthFt) }

    /** Act [affordance] on [bed], the state the client's ETag was checked against. */
    suspend fun perform(bed: GardenBedState, affordance: String, input: ActInput): ActResult =
        when (affordance) {
            "sowSeed" -> input.bind(SowSeedRequest::class.java)
                .let { ActResult(service.sowSeed(bed, it.seedType, it.depthCm)) }
            "waterCell" -> input.bind(WaterCellRequest::class.java)
                .let { ActResult(service.waterCell(bed, it.milliliters)) }
            "harvest" -> service.harvest(bed).let { (state, result) -> ActResult(state, result) }
            else -> error("no act $affordance")
        }
}
