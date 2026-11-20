package com.artie.baseball.store

import com.artie.baseball.domain.GameDirectory
import com.artie.baseball.domain.GameSetup
import com.artie.baseball.domain.Player
import com.artie.baseball.domain.Side
import com.artie.baseball.domain.Team
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

/** Games set up in Postgres: tables `game` and `player` (migration V1), each lineup in batting order. */
class PostgresGameDirectory(private val db: DatabaseClient) : GameDirectory {

    private data class Row(val id: Long, val label: String, val home: String, val away: String)
    private data class Batter(val gameId: Long, val side: String, val order: Int, val player: Player)

    override suspend fun find(id: Long): GameSetup? = all().firstOrNull { it.id == id }

    override suspend fun all(): List<GameSetup> {
        val games = db.sql("SELECT id, label, home_name, away_name FROM game ORDER BY id")
            .map { r -> Row(long(r.get("id")), str(r.get("label")), str(r.get("home_name")), str(r.get("away_name"))) }
            .flow().toList()
        val batters = db.sql("SELECT game_id, side, batting_order, number, name FROM player")
            .map { r ->
                Batter(
                    long(r.get("game_id")),
                    str(r.get("side")),
                    int(r.get("batting_order")),
                    Player(int(r.get("number")), str(r.get("name"))),
                )
            }
            .flow().toList()
        return games.map { g ->
            fun lineup(side: String) =
                batters.filter { it.gameId == g.id && it.side == side }.sortedBy { it.order }.map { it.player }
            GameSetup(g.id, g.label, Team(g.home, lineup(Side.HOME)), Team(g.away, lineup(Side.AWAY)))
        }
    }

    private companion object {
        fun long(value: Any?): Long = (value as Number).toLong()
        fun int(value: Any?): Int = (value as Number).toInt()
        fun str(value: Any?): String = value as String
    }
}
