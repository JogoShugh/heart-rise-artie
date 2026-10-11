package com.artie.gardenbed.tck

import com.artie.gardenbed.bdd.BedEventLog
import com.artie.gardenbed.bdd.InMemoryBeds
import com.artie.gardenbed.bdd.InMemoryGardenBedsConfig
import com.artie.gardenbed.bdd.putBed
import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.declaration.BedActs
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * The garden runs HEART's conformance features (heart-tck) against itself:
 * the same scenarios every domain server runs, with nothing in them about beds.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.gardenbed.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunHeartTckTest

/** Bed 2, planted: watering is offered, sowing is not. */
@TestConfiguration
class GardenFixture {
    @Bean
    fun gardenTckFixture(beds: InMemoryBeds, events: BedEventLog): TckFixture = object : TckFixture {
        override val collection = "beds"
        override val offered = TckAct(
            key = "garden:water-cell",
            path = "water-cell",
            body = """{"milliliters": 1}""",
            invalidBody = """{"milliliters": 0}""",
            invalidPointer = "/milliliters",
        )
        override val absent = TckAct(
            key = "garden:sow-seed",
            path = "sow-seed",
            body = """{"seedType": "kale"}""",
            reason = "The bed is already planted.",
        )
        override val searchTerm = "Bed 2"

        override suspend fun ready(): Long {
            beds.reset()
            events.reset()
            putBed(beds, events, 2, GardenBedStatus.PLANTED)
            return 2
        }
    }
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGardenBedsConfig::class, BedActs::class, GardenFixture::class, TckWorld::class,
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class,
)
@TestPropertySource(properties = ["heart.coap.port=0"])
class GardenTckContext
