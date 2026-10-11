package com.artie.basketball.tck

import com.artie.basketball.bdd.InMemoryGames
import com.artie.basketball.config.HeartConfig
import com.artie.basketball.domain.GAMES
import com.artie.basketball.domain.GameEvent
import com.artie.basketball.domain.GameSetup
import com.artie.basketball.domain.Team
import com.artie.basketball.store.InMemoryGameDirectory
import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
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

/** Basketball runs HEART's conformance features (heart-tck) against itself. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.basketball.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
// Nothing here is created from the bookmark, so the @creation scenarios do not apply.
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @creation")
class RunHeartTckTest

/** Game 1, live: scoring a basket is offered, tipping off is not. */
@TestConfiguration
class GameFixture {
    @Bean
    fun gameTckFixture(games: InMemoryGameDirectory, events: InMemoryEventStore): TckFixture = object : TckFixture {
        override val collection = "games"
        override val offered = TckAct(
            key = "basketball:score-basket",
            path = "score-basket",
            body = """{"team": "home", "playerNumber": 23, "points": 2}""",
            invalidBody = """{"team": "home", "playerNumber": 23, "points": 1}""",
            invalidPointer = "/points",
        )
        override val absent = TckAct(
            key = "basketball:tip-off",
            path = "tip-off",
            body = """{"possession": "home"}""",
            reason = "The game has already started.",
        )
        override val searchTerm = "Eagles"

        override suspend fun ready(): Long {
            games.clear()
            events.forget(GAMES.stream)
            games.add(
                GameSetup(
                    1, "Eagles v Marauders",
                    Team("Eagles", mapOf(4 to "Avery", 23 to "Riley")),
                    Team("Marauders", mapOf(3 to "Taylor", 7 to "Morgan")),
                ),
            )
            events.append(GAMES, 1, 1, GameEvent.TippedOff("home"))
            return 1
        }
    }
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGames::class, GameFixture::class, TckWorld::class,
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class GameTckContext
