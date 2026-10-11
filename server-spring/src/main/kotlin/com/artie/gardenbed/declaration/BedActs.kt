package com.artie.gardenbed.declaration

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.service.GardenBedService
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

    suspend fun perform(id: Long, affordance: String, input: ActInput): ActResult =
        when (affordance) {
            "sowSeed" -> input.bind(SowSeedRequest::class.java)
                .let { ActResult(service.sowSeed(id, it.seedType, it.depthCm)) }
            "waterCell" -> input.bind(WaterCellRequest::class.java)
                .let { ActResult(service.waterCell(id, it.milliliters)) }
            "harvest" -> service.harvest(id).let { (state, result) -> ActResult(state, result) }
            else -> error("no act $affordance")
        }
}
