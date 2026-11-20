package com.artie.basketball.store

import com.artie.basketball.domain.GameEvent
import com.artie.basketball.domain.GameService
import com.artie.basketball.domain.Phase
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
 * directory, and the library's Postgres event store.
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
        db.sql("INSERT INTO game (id, label, home_name, away_name) VALUES (1, 'Eagles v Marauders', 'Eagles', 'M')")
            .await()
        db.sql("INSERT INTO player (game_id, side, number, name) VALUES (1, 'home', 23, 'Riley')").await()

        val live = games.record(games.state(1)) { GameEvent.TippedOff("home") }
        val after = games.record(live) { GameEvent.BasketScored("home", 23, 3) }

        assertEquals(Phase.LIVE, after.phase)
        assertEquals(3, after.score("home"))
        assertEquals("Riley", after.setup?.home?.roster?.get(23))
        assertEquals(listOf(1, 2), games.history(1).map { it.sequenceNo })
    }
}
