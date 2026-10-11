package com.artie.gardenbed.config

import com.artie.gardenbed.web.GardenBeds
import com.artie.heart.Layers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Declares the garden bed to the HEART library. heart-spring serves its
 * affordance sets, schemas, absence explanations and ALPS profile from this
 * one bean; the garden has no controllers for them.
 */
@Configuration
class HeartConfig {
    @Bean
    fun gardenBeds(): Layers<*, *> = GardenBeds.layers
}
