package com.artie.gardenbed.bdd

import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.web.BedActs
import com.artie.gardenbed.web.GardenRoutes
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.spring.HeartAutoConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** One Spring WebFlux slice for every scenario: heart-spring, which serves
 *  the service index, the searches, the beds and their acts, Layers 2 and 3
 *  and the ALPS profile, beside the garden's own history routes, wired to
 *  the in-memory store. The garden has no controllers. */
@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGardenBedsConfig::class, HttpWorld::class, BedActs::class, GardenRoutes::class,
    // The garden's declaration, served over HTTP and CoAP by the HEART library.
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class,
)
// SPEC §6.0.1: the CoAP front door on a free UDP port, beside the HTTP slice.
@TestPropertySource(properties = ["heart.coap.port=0"])
class CucumberSpringConfig
