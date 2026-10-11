package com.artie.gardenbed.bdd

import com.artie.gardenbed.coap.CoapFrontDoor
import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.web.BedActs
import com.artie.gardenbed.web.GardenRoutes
import com.artie.gardenbed.web.ServiceIndexController
import com.artie.heart.spring.HeartAutoConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** One Spring WebFlux slice for every scenario: the service index, the
 *  garden's own history routes, and heart-spring, which serves the beds,
 *  their acts, Layers 2 and 3 and the ALPS profile, wired to the in-memory store. */
@CucumberContextConfiguration
@WebFluxTest(
    controllers = [
        ServiceIndexController::class,
    ],
)
@Import(
    InMemoryGardenBedsConfig::class, HttpWorld::class, BedActs::class, GardenRoutes::class,
    CoapFrontDoor::class,
    // The garden's declaration, served by the HEART library's own controllers.
    HeartConfig::class, HeartAutoConfiguration::class,
)
// SPEC §6.0.1: the CoAP front door on a free UDP port, beside the HTTP slice.
@TestPropertySource(properties = ["heart.coap.port=0"])
class CucumberSpringConfig
