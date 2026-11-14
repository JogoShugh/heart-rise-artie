package com.artie.basketball.domain

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded

/** One player's tally in a game. */
data class PlayerStats(val points: Int = 0, val fouls: Int = 0)

/** A play that put points on the board; it can be taken back once. */
data class ScoringPlay(
    val sequenceNo: Int,
    val team: String,
    val playerNumber: Int,
    val points: Int,
    val takenBack: Boolean = false,
)

/**
 * A game as its events fold: the phase, the period, possession, every
 * player's points and fouls (keyed `home:23`), each team's fouls this half,
 * and the scoring plays. [setup] is the game's teams, set on the folded state
 * when it is read; [version] is the number of the last event.
 */
data class GameState(
    val id: Long,
    val setup: GameSetup? = null,
    val phase: Phase = Phase.SCHEDULED,
    val period: Int = 0,
    val possession: String? = null,
    val stats: Map<String, PlayerStats> = emptyMap(),
    val teamFouls: Map<String, Int> = emptyMap(),
    val plays: List<ScoringPlay> = emptyList(),
    val version: Int = 0,
) {
    fun score(side: String): Int = plays.filter { it.team == side && !it.takenBack }.sumOf { it.points }

    fun player(side: String, number: Int): PlayerStats = stats["$side:$number"] ?: PlayerStats()

    fun fouledOut(side: String, number: Int): Boolean = player(side, number).fouls >= Rules.FOUL_OUT

    fun teamFouls(side: String): Int = teamFouls[side] ?: 0

    /** A team is in the bonus once the other team has reached the bonus count this half. */
    fun inBonus(side: String): Boolean = teamFouls(Side.other(side)) >= Rules.BONUS
}

/** The fold: one event at a time, in order. */
object GameProjector {
    fun apply(state: GameState, recorded: Recorded<GameEvent>): GameState =
        play(state, recorded).copy(version = recorded.sequenceNo)

    private fun play(s: GameState, recorded: Recorded<GameEvent>): GameState =
        when (val e = recorded.event) {
            is GameEvent.TippedOff -> s.copy(phase = Phase.LIVE, period = 1, possession = e.possession)
            is GameEvent.BasketScored -> scored(s, recorded.sequenceNo, e.team, e.playerNumber, e.points)
                .copy(possession = Side.other(e.team))
            is GameEvent.FreeThrowTaken ->
                if (e.made) scored(s, recorded.sequenceNo, e.team, e.playerNumber, 1) else s
            is GameEvent.FoulRecorded -> fouled(s, e.team, e.playerNumber)
            is GameEvent.TurnoverRecorded -> s.copy(possession = Side.other(e.team))
            is GameEvent.TimeoutCalled -> s.copy(phase = Phase.TIMEOUT)
            GameEvent.PlayResumed -> s.copy(phase = Phase.LIVE)
            GameEvent.PeriodEnded -> s.copy(phase = if (s.period < Rules.PERIODS) Phase.BREAK else Phase.FULL_TIME)
            GameEvent.PeriodStarted -> started(s)
            GameEvent.FinalWhistle -> s.copy(phase = Phase.FINAL)
            is GameEvent.PlayTakenBack -> takenBack(s, e.sequenceNo)
        }

    private fun scored(s: GameState, sequenceNo: Int, team: String, number: Int, points: Int): GameState {
        val key = "$team:$number"
        val stats = s.player(team, number).let { it.copy(points = it.points + points) }
        return s.copy(stats = s.stats + (key to stats), plays = s.plays + ScoringPlay(sequenceNo, team, number, points))
    }

    private fun fouled(s: GameState, team: String, number: Int): GameState {
        val stats = s.player(team, number).let { it.copy(fouls = it.fouls + 1) }
        return s.copy(
            stats = s.stats + ("$team:$number" to stats),
            teamFouls = s.teamFouls + (team to s.teamFouls(team) + 1),
        )
    }

    /** A new period; team fouls start again after half time. */
    private fun started(s: GameState): GameState {
        val period = s.period + 1
        val fouls = if (period == Rules.HALF + 1) emptyMap() else s.teamFouls
        return s.copy(phase = Phase.LIVE, period = period, teamFouls = fouls)
    }

    private fun takenBack(s: GameState, sequenceNo: Int): GameState {
        val play = s.plays.first { it.sequenceNo == sequenceNo }
        val stats = s.player(play.team, play.playerNumber).let { it.copy(points = it.points - play.points) }
        return s.copy(
            stats = s.stats + ("${play.team}:${play.playerNumber}" to stats),
            plays = s.plays.map { if (it.sequenceNo == sequenceNo) it.copy(takenBack = true) else it },
        )
    }
}

/** How a game is event-sourced (heart-eventsourcing): its stream, its event types, and the fold. */
val GAMES: EventSourcing<GameEvent, GameState> = EventSourcing(
    stream = "game",
    types = mapOf(
        "TippedOff" to GameEvent.TippedOff::class.java,
        "BasketScored" to GameEvent.BasketScored::class.java,
        "FreeThrowTaken" to GameEvent.FreeThrowTaken::class.java,
        "FoulRecorded" to GameEvent.FoulRecorded::class.java,
        "TurnoverRecorded" to GameEvent.TurnoverRecorded::class.java,
        "TimeoutCalled" to GameEvent.TimeoutCalled::class.java,
        "PlayResumed" to GameEvent.PlayResumed::class.java,
        "PeriodEnded" to GameEvent.PeriodEnded::class.java,
        "PeriodStarted" to GameEvent.PeriodStarted::class.java,
        "FinalWhistle" to GameEvent.FinalWhistle::class.java,
        "PlayTakenBack" to GameEvent.PlayTakenBack::class.java,
    ),
    stateClass = GameState::class.java,
    empty = { id -> GameState(id) },
    apply = GameProjector::apply,
)
