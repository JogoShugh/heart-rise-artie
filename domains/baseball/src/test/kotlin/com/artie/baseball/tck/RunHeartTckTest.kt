package com.artie.baseball.tck

import com.artie.baseball.bdd.InMemoryGames
import com.artie.baseball.bdd.TestCredentials
import com.artie.baseball.config.HeartConfig
import com.artie.baseball.domain.GAMES
import com.artie.baseball.domain.GameEvent
import com.artie.baseball.domain.GameSetup
import com.artie.baseball.domain.Player
import com.artie.baseball.domain.Team
import com.artie.baseball.store.InMemoryGameDirectory
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

/** Baseball runs HEART's conformance features (heart-tck) against itself. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.baseball.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
// The bookmark offers no act here, so the @bookmark-act scenarios do not apply.
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @bookmark-act")
class RunHeartTckTest

/** Game 1, under way: a ball in play is offered, play ball is not. */
@TestConfiguration
class GameFixture {
    @Bean
    fun gameTckFixture(games: InMemoryGameDirectory, events: InMemoryEventStore): TckFixture = object : TckFixture {
        override val collection = "games"
        override val offered = TckAct(
            key = "baseball:hit",
            path = "hit",
            body = """{"result": "single"}""",
            invalidBody = """{"result": "bunt"}""",
            invalidPointer = "/result",
        )
        override val absent = TckAct(
            key = "baseball:play-ball",
            path = "play-ball",
            body = "{}",
            reason = "The game has already started.",
        )
        override val credential = TestCredentials.CREDENTIAL
        override val searchTerm = "Cubs"

        override suspend fun ready(): Long {
            games.clear()
            events.forget(GAMES.stream)
            games.add(
                GameSetup(
                    1,
                    "Cubs v Bears",
                    Team("Cubs", listOf(Player(1, "Avery"), Player(3, "Jordan"))),
                    Team("Bears", listOf(Player(2, "Taylor"), Player(5, "Morgan"))),
                ),
            )
            events.append(GAMES, 1, 1, GameEvent.PlayBall)
            return 1
        }
    }
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryGames::class,
    GameFixture::class,
    TckWorld::class,
    HeartConfig::class,
    HeartAutoConfiguration::class,
    HeartCoapAutoConfiguration::class,
    TestCredentials::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class GameTckContext
