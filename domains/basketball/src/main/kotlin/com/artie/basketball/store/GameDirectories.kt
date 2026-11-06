package com.artie.basketball.store

import com.artie.basketball.domain.GameDirectory
import com.artie.basketball.domain.GameSetup
import com.artie.basketball.domain.Side
import com.artie.basketball.domain.Team
import kotlinx.coroutines.flow.toList
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import java.util.concurrent.ConcurrentHashMap

/** Games set up in memory: for tests and demos. */
class InMemoryGameDirectory : GameDirectory {
    private val games = ConcurrentHashMap<Long, GameSetup>()

    fun add(game: GameSetup) {
        games[game.id] = game
    }

    fun clear() = games.clear()

    override suspend fun find(id: Long): GameSetup? = games[id]

    override suspend fun all(): List<GameSetup> = games.values.toList()
}

/** Games set up in Postgres: tables `game` and `player` (migration V1). */
class PostgresGameDirectory(private val db: DatabaseClient) : GameDirectory {

    private data class Row(val id: Long, val label: String, val home: String, val away: String)
    private data class Player(val gameId: Long, val side: String, val number: Int, val name: String)

    override suspend fun find(id: Long): GameSetup? = all().firstOrNull { it.id == id }

    override suspend fun all(): List<GameSetup> {
        val games = db.sql("SELECT id, label, home_name, away_name FROM game ORDER BY id")
            .map { r -> Row(long(r.get("id")), str(r.get("label")), str(r.get("home_name")), str(r.get("away_name"))) }
            .flow().toList()
        val players = db.sql("SELECT game_id, side, number, name FROM player")
            .map { r -> Player(long(r.get("game_id")), str(r.get("side")), int(r.get("number")), str(r.get("name"))) }
            .flow().toList()
        return games.map { g ->
            fun roster(side: String) =
                players.filter { it.gameId == g.id && it.side == side }.associate { it.number to it.name }
            GameSetup(g.id, g.label, Team(g.home, roster(Side.HOME)), Team(g.away, roster(Side.AWAY)))
        }
    }

    private companion object {
        fun long(value: Any?): Long = (value as Number).toLong()
        fun int(value: Any?): Int = (value as Number).toInt()
        fun str(value: Any?): String = value as String
    }
}
