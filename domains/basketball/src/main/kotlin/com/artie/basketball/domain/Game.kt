package com.artie.basketball.domain

import com.artie.heart.ResourceNotFoundException

/** The two sides of a game, as they appear on the wire and in events. */
object Side {
    const val HOME = "home"
    const val AWAY = "away"

    fun other(side: String): String = if (side == HOME) AWAY else HOME
}

/** Youth rules (plan B2): four periods, five fouls and out, bonus from seven team fouls in a half. */
object Rules {
    const val PERIODS = 4
    const val FOUL_OUT = 5
    const val BONUS = 7
    const val HALF = 2
}

/** A team and its roster: shirt number to player name. */
data class Team(val name: String, val roster: Map<Int, String>)

/** A game's identity and its two teams. Not an event: it is set up before the game. */
data class GameSetup(val id: Long, val label: String, val home: Team, val away: Team) {
    fun team(side: String): Team = if (side == Side.HOME) home else away
}

/** Where a game is: which acts the scorer may record depends on this alone. */
enum class Phase { SCHEDULED, LIVE, TIMEOUT, BREAK, FULL_TIME, FINAL }

/** 404: no game with that id. */
class GameNotFoundException(id: Long) : ResourceNotFoundException("No game with id $id")

/** The games that have been set up. */
interface GameDirectory {
    suspend fun find(id: Long): GameSetup?

    suspend fun all(): List<GameSetup>
}
