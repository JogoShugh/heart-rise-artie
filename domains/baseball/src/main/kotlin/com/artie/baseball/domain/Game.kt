package com.artie.baseball.domain

import com.artie.heart.ResourceNotFoundException

/** The two sides of a game, as they appear on the wire and in events. The visitors bat first. */
object Side {
    const val HOME = "home"
    const val AWAY = "away"
}

/** League rules for 8U coach pitch: set here, in one place, so a league's own numbers drop in. */
object Rules {
    const val INNINGS = 6
    const val OUTS = 3
    const val RUN_LIMIT = 5
    const val PITCHES = 6

    /** The run limit holds in every inning but the last, which is played out. */
    fun runLimit(inning: Int): Int? = if (inning < INNINGS) RUN_LIMIT else null
}

/** The bases, in order; a hit moves every runner up as many bases as the batter. */
object Bases {
    val names = listOf("first", "second", "third")

    fun indexOf(name: String): Int = names.indexOf(name)
}

/** A player in the batting order. */
data class Player(val number: Int, val name: String)

/** A team and its batting order: the whole team bats, in this order. */
data class Team(val name: String, val lineup: List<Player>)

/** A game's identity and its two teams. Not an event: it is set up before the game. */
data class GameSetup(val id: Long, val label: String, val home: Team, val away: Team) {
    fun team(side: String): Team = if (side == Side.HOME) home else away
}

/** Where a game is: which acts the scorer may record depends on this alone. */
enum class Phase { SCHEDULED, PLAY, HALF_OVER, GAME_OVER, FINAL }

/** 404: no game with that id. */
class GameNotFoundException(id: Long) : ResourceNotFoundException("No game with id $id")

/** The games that have been set up. */
interface GameDirectory {
    suspend fun find(id: Long): GameSetup?

    suspend fun all(): List<GameSetup>
}
