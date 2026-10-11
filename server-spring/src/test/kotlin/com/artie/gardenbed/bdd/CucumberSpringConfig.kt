package com.artie.gardenbed.bdd

import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.declaration.BedActs
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.spring.HeartAutoConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** One Spring WebFlux slice for every scenario: heart-spring, which serves
 *  the service index, the searches, the beds and their acts, Layers 2 and 3
 *  and the ALPS profile, and each bed's past, wired to the in-memory store.
 *  The garden has no controllers. */
@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGardenBedsConfig::class, HttpWorld::class, BedActs::class,
    // The garden's declaration, served over HTTP and CoAP by the HEART library.
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class,
)
// SPEC §6.0.1: the CoAP front door on a free UDP port, beside the HTTP slice.
@TestPropertySource(properties = ["heart.coap.port=0"])
class CucumberSpringConfig
