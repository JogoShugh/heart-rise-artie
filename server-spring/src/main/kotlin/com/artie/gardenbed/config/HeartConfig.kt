package com.artie.gardenbed.config

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.declaration.BedActs
import com.artie.gardenbed.declaration.GardenBeds
import com.artie.gardenbed.declaration.GardenChange
import com.artie.gardenbed.domain.GARDEN_BEDS
import com.artie.gardenbed.service.GardenBedService
import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.OpenApiDocument
import com.artie.heart.ServingLimits
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the garden bed to the HEART library: its declaration, how a bed is
 * read, what each act does, how beds are found, and a bed's past, since its
 * state is folded from events. heart-spring and heart-coap serve the rest,
 * from the service index to the ALPS profile; the garden has no controllers.
 *
 * `garden.frost.enabled` is Tuesday: a hard frost can freeze a planted bed,
 * which offers only thaw. It changes the declaration, and so the OpenAPI
 * description's version: 1.0.0 without frost, 1.1.0 with it.
 */
@Configuration
class HeartConfig(
    @Value("\${garden.frost.enabled:false}") private val frost: Boolean,
    // Wednesday: one more kind of change, released on its own from Monday (GARDEN_CHANGE).
    @Value("\${garden.change:}") changeName: String,
) {
    private val change = GardenChange.of(changeName)

    @Bean
    fun gardenBeds(service: GardenBedService, acts: BedActs): HeartResource<GardenBedState, GardenBedStatus> =
        HeartResource(
            layers = GardenBeds.layers(frost, change),
            load = { id -> service.currentState(id) },
            perform = { bed, act, input ->
                val done = acts.perform(bed, act, input)
                // A harvest also says what it yielded.
                val harvest = done.harvest?.let {
                    mapOf("X-Harvest-Yield-Grams" to it.yieldGrams.toString(), "X-Harvest-Crop-Name" to it.cropName)
                }
                Acted(done.state, harvest.orEmpty())
            },
            // A stored bed always has its id; only a bed not yet saved has none.
            find = { name, id -> service.findBeds(name, id).map { Found(checkNotNull(it.id), it.label) } },
            past = GARDEN_BEDS.past(at = { id, asOf -> service.currentState(id, asOf) }, history = service::history),
            performAtBookmark = { _, input -> Acted(acts.build(input)) },
        )

    /**
     * The garden's OpenAPI description: the frost is a change to the API, so it is a new version.
     * Affordance links change its bytes too, so they are marked in the version, after a `+`.
     */
    @Bean
    fun openApiDocument(
        gardenBeds: HeartResource<GardenBedState, GardenBedStatus>,
        limits: ServingLimits,
    ): OpenApiDocument {
        val release = when {
            change != null -> "$WEDNESDAY-${change.property}"
            frost -> TUESDAY
            else -> MONDAY
        }
        return OpenApiDocument(
            listOf(gardenBeds),
            version = if (limits.affordanceLinks) "$release+affordance-links" else release,
            affordanceLinks = limits.affordanceLinks,
        )
    }

    private companion object {
        const val MONDAY = "1.0.0"
        const val TUESDAY = "1.1.0"
        const val WEDNESDAY = "1.2.0"
    }
}
