package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.service.GardenBedService
import com.artie.gardenbed.web.dto.HarvestResult
import com.artie.gardenbed.web.dto.SowSeedRequest
import com.artie.gardenbed.web.dto.WaterCellRequest
import com.artie.heart.ActPipeline
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/** What an act left behind: the bed's new state and, for a harvest, what was harvested. */
data class ActResult(val state: GardenBedState, val harvest: HarvestResult? = null)

/**
 * The acts on a bed, shared by every binding: HTTP (GardenBedController) and
 * CoAP (CoapFrontDoor). heart-core's [ActPipeline] runs the checks of SPEC
 * §6.2 in order; this class says only how a bed is read and what each act does.
 */
@Component
class BedActs(private val service: GardenBedService, objectMapper: ObjectMapper) {
    private val pipeline = ActPipeline(GardenBeds.type, objectMapper)

    /**
     * No validator, then a stale one, then an act not offered; the body is
     * checked inside [run]. [matches] compares the act's [validator] with the
     * one the binding selects for the current state, which is read only when
     * a validator is present.
     */
    suspend fun <V : Any> check(id: Long, affordance: String, validator: V?, matches: (GardenBedState, V) -> Boolean) {
        pipeline.check(affordance, validator, { service.currentState(id) }, matches)
    }

    /** Checks [body] against the act's schema and performs the act. A concurrent write is stale. */
    suspend fun run(id: Long, affordance: String, body: String?): ActResult =
        pipeline.run(affordance, body) { input ->
            when (affordance) {
                "sowSeed" -> input.bind(SowSeedRequest::class.java)
                    .let { ActResult(service.sowSeed(id, it.seedType, it.depthCm)) }
                "waterCell" -> input.bind(WaterCellRequest::class.java)
                    .let { ActResult(service.waterCell(id, it.milliliters)) }
                "harvest" -> service.harvest(id).let { (state, result) -> ActResult(state, result) }
                else -> error("no act $affordance")
            }
        }

    companion object {
        /** The act each action path names: `/beds/{id}/actions/{path}`. */
        val BY_PATH: Map<String, String> = GardenBeds.type.acts.associate { it.path to it.name }
    }
}
