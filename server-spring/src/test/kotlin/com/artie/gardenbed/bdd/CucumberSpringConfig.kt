package com.artie.gardenbed.bdd

import com.artie.gardenbed.config.HateoasConfig
import com.artie.gardenbed.web.AffordanceSetController
import com.artie.gardenbed.web.GardenBedController
import com.artie.gardenbed.web.SchemaController
import com.artie.gardenbed.web.ServiceIndexController
import com.artie.gardenbed.web.ServingLimits
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import

/** One Spring WebFlux slice for every scenario: the controllers that serve
 *  the service index and Layers 1-3, wired to the in-memory store. */
@CucumberContextConfiguration
@WebFluxTest(
    controllers = [
        ServiceIndexController::class,
        GardenBedController::class,
        AffordanceSetController::class,
        SchemaController::class,
    ],
)
@Import(HateoasConfig::class, InMemoryGardenBedsConfig::class, HttpWorld::class, ServingLimits::class)
class CucumberSpringConfig
