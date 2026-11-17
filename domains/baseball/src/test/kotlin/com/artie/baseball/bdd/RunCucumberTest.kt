package com.artie.baseball.bdd

import com.artie.baseball.config.HeartConfig
import com.artie.baseball.domain.GameService
import com.artie.baseball.store.InMemoryGameDirectory
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.spring.HeartAutoConfiguration
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

/** Runs the baseball features. The bdd-gates log task reads this class's JUnit XML. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.baseball.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunCucumberTest

/** Games and their events in memory: the real rules and the real HEART library, no Postgres. */
@TestConfiguration
class InMemoryGames {
    @Bean
    fun gameDirectory() = InMemoryGameDirectory()

    @Bean
    fun eventStore() = InMemoryEventStore()

    @Bean
    fun gameService(games: InMemoryGameDirectory, events: InMemoryEventStore) = GameService(games, events)
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGames::class,
    HeartConfig::class,
    HeartAutoConfiguration::class,
    HeartCoapAutoConfiguration::class,
    TestCredentials::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class CucumberSpringConfig
