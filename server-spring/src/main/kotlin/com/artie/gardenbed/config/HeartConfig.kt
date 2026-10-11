package com.artie.gardenbed.config

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.declaration.BedActs
import com.artie.gardenbed.declaration.GardenBeds
import com.artie.gardenbed.domain.GARDEN_BEDS
import com.artie.gardenbed.service.GardenBedService
import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Past
import com.artie.heart.PastEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the garden bed to the HEART library: its declaration, how a bed is
 * read, what each act does, how beds are found, and a bed's past, since its
 * state is folded from events. heart-spring and heart-coap serve the rest,
 * from the service index to the ALPS profile; the garden has no controllers.
 */
@Configuration
class HeartConfig {
    @Bean
    fun gardenBeds(service: GardenBedService, acts: BedActs): HeartResource<GardenBedState, GardenBedStatus> =
        HeartResource(
            layers = GardenBeds.layers,
            load = { id -> service.currentState(id) },
            perform = { id, act, input ->
                val done = acts.perform(id, act, input)
                // A harvest also says what it yielded.
                val harvest = done.harvest?.let {
                    mapOf("X-Harvest-Yield-Grams" to it.yieldGrams.toString(), "X-Harvest-Crop-Name" to it.cropName)
                }
                Acted(done.state, harvest.orEmpty())
            },
            // A stored bed always has its id; only a bed not yet saved has none.
            find = { name, id -> service.findBeds(name, id).map { Found(checkNotNull(it.id), it.label) } },
            past = Past(
                at = { id, instant -> service.stateAt(id, instant) },
                history = { id ->
                    service.history(id).map {
                        PastEvent(it.sequenceNo, GARDEN_BEDS.typeOf(it.event), it.occurredAt, it.event)
                    }
                },
            ),
        )
}
