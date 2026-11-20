package com.artie.baseball.store

import com.artie.baseball.domain.GameEvent
import com.artie.baseball.domain.GameService
import com.artie.baseball.domain.Phase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The whole application on real Postgres: migration V1, the Postgres game
 * directory with its lineups in batting order, and the library's Postgres event store.
 */
@SpringBootTest(properties = ["heart.coap.port=-1"])
@Testcontainers(disabledWithoutDocker = true)
class PostgresGamesTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var games: GameService

    @Autowired
    lateinit var db: DatabaseClient

    @Test
    fun `a game set up in Postgres is played through the event store`() = runBlocking {
        db.sql("INSERT INTO game (id, label, home_name, away_name) VALUES (1, 'Cubs v Bears', 'Cubs', 'Bears')").await()
        db.sql(
            "INSERT INTO player (game_id, side, batting_order, number, name) VALUES " +
                "(1, 'away', 2, 5, 'Morgan'), (1, 'away', 1, 2, 'Taylor'), (1, 'home', 1, 1, 'Avery')",
        ).await()

        val started = games.record(games.state(1)) { GameEvent.PlayBall }
        val after = games.record(started) { GameEvent.Hit("home-run") }

        assertEquals(Phase.PLAY, after.phase)
        assertEquals(1, after.score("away"))
        assertEquals("Morgan", after.batterUp().name)
        assertEquals(listOf(1, 2), games.history(1).map { it.sequenceNo })
    }
}
