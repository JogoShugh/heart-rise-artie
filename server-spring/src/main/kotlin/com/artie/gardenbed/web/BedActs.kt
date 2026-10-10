package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.eventstore.ConcurrentAppendException
import com.artie.gardenbed.service.GardenBedService
import com.artie.gardenbed.service.InvalidTransitionException
import com.artie.gardenbed.web.dto.HarvestResult
import com.artie.gardenbed.web.dto.SowSeedRequest
import com.artie.gardenbed.web.dto.WaterCellRequest
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.StaleWriteConflict
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Validator
import org.springframework.stereotype.Component

/** What an act left behind: the bed's new state and, for a harvest, what was harvested. */
data class ActResult(val state: GardenBedState, val harvest: HarvestResult? = null)

/**
 * The acts on a bed and the checks before them (SPEC §6.2), shared by every
 * binding: HTTP (GardenBedController) and CoAP (CoapFrontDoor). Each binding
 * says what its validator is and how it is compared; the order of the checks
 * and what they refuse are the same everywhere.
 */
@Component
class BedActs(private val service: GardenBedService, objectMapper: ObjectMapper, validator: Validator) {
    private val binder = ActBodyBinder(objectMapper, validator)

    /**
     * SPEC §6.2 evaluation order: no validator, then a stale one, then an act
     * not offered; the body is checked inside [run]. [matches] compares the
     * act's validator with the one the binding selects for the current state.
     * The state is read only when a validator is present.
     */
    suspend fun check(id: Long, affordance: String, hasValidator: Boolean, matches: (GardenBedState) -> Boolean) {
        val failure = if (!hasValidator) {
            PreconditionRequiredException("$affordance on bed $id needs If-Match with the bed's current ETag")
        } else {
            val current = service.currentState(id)
            when {
                !matches(current) -> StaleWriteConflict(
                    "the validator does not match the current one of the representation selected for bed $id",
                )
                !offered(affordance, current) -> InvalidTransitionException(
                    "$affordance is not in bed $id's current affordance set (status ${current.status})",
                    rel = GardenProfile.reference(affordance),
                )
                else -> null
            }
        }
        failure?.let { throw it }
    }

    /** Binds [body] to the act's input and performs the act. A concurrent write is stale. */
    suspend fun run(id: Long, affordance: String, body: String?): ActResult =
        try {
            when (affordance) {
                "sowSeed" -> binder.bind(body, SowSeedRequest::class.java)
                    .let { ActResult(service.sowSeed(id, it.seedType, it.depthCm)) }
                "waterCell" -> binder.bind(body, WaterCellRequest::class.java)
                    .let { ActResult(service.waterCell(id, it.milliliters)) }
                "harvest" -> service.harvest(id).let { (state, result) -> ActResult(state, result) }
                else -> error("no act $affordance")
            }
        } catch (e: ConcurrentAppendException) {
            throw StaleWriteConflict(e.message ?: "concurrent write conflict", e)
        }

    private fun offered(affordance: String, state: GardenBedState): Boolean =
        affordance in JsonSchemaDialectSupport.allowedAffordances(state.status)

    companion object {
        /** The act each action path names: `/beds/{id}/actions/{path}`. */
        val BY_PATH: Map<String, String> =
            mapOf("sow-seed" to "sowSeed", "water-cell" to "waterCell", "harvest" to "harvest")
    }
}
