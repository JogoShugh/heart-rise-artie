package com.artie.gardenbed.bdd

import com.artie.gardenbed.config.HateoasConfig
import com.artie.gardenbed.web.AffordanceSetController
import com.artie.gardenbed.web.GardenBedController
import com.artie.gardenbed.web.SchemaController
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import

/** One Spring WebFlux slice for every scenario: the three controllers that
 *  serve Layers 1-3, wired to the in-memory store. */
@CucumberContextConfiguration
@WebFluxTest(controllers = [GardenBedController::class, AffordanceSetController::class, SchemaController::class])
@Import(HateoasConfig::class, InMemoryGardenBedsConfig::class, HttpWorld::class)
class CucumberSpringConfig
