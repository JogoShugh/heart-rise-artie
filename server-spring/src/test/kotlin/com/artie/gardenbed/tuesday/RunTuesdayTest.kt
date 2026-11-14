package com.artie.gardenbed.tuesday

import com.artie.gardenbed.bdd.HttpWorld
import com.artie.gardenbed.bdd.InMemoryGardenBedsConfig
import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.declaration.BedActs
import com.artie.gardenbed.service.GardenWeather
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.spring.HeartAutoConfiguration
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/**
 * Tuesday: the @tuesday scenarios, against the garden after the team shipped
 * the frost (garden.frost.enabled=true). The steps are Monday's own; only the
 * server differs. The bdd-gates logging task reads this class's JUnit XML too.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.gardenbed.bdd,com.artie.gardenbed.tuesday")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@tuesday")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunTuesdayTest

/** The garden with frost, served over HTTP and CoAP by the HEART library, and the weather its operator reports. */
@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGardenBedsConfig::class, HttpWorld::class, BedActs::class,
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class, GardenWeather::class,
)
// Served over CoAP too, as on Monday, on a free UDP port.
@TestPropertySource(properties = ["garden.frost.enabled=true", "heart.coap.port=0"])
class TuesdaySpringConfig
