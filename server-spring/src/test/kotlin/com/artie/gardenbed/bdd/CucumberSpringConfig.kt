package com.artie.gardenbed.bdd

import com.artie.gardenbed.coap.CoapFrontDoor
import com.artie.gardenbed.config.HateoasConfig
import com.artie.gardenbed.web.AffordanceSetController
import com.artie.gardenbed.web.BedActs
import com.artie.gardenbed.web.GardenBedController
import com.artie.gardenbed.web.ProfileController
import com.artie.gardenbed.web.SchemaController
import com.artie.gardenbed.web.ServiceIndexController
import com.artie.gardenbed.web.ServingLimits
import com.artie.gardenbed.web.UnavailableController
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** One Spring WebFlux slice for every scenario: the controllers that serve
 *  the service index, Layers 1-3 and the ALPS profile, wired to the in-memory store. */
@CucumberContextConfiguration
@WebFluxTest(
    controllers = [
        ServiceIndexController::class,
        GardenBedController::class,
        AffordanceSetController::class,
        SchemaController::class,
        ProfileController::class,
        UnavailableController::class,
    ],
)
@Import(
    HateoasConfig::class, InMemoryGardenBedsConfig::class, HttpWorld::class, ServingLimits::class, BedActs::class,
    CoapFrontDoor::class,
)
// SPEC §6.0.1: the CoAP front door on a free UDP port, beside the HTTP slice.
@TestPropertySource(properties = ["heart.coap.port=0"])
class CucumberSpringConfig
