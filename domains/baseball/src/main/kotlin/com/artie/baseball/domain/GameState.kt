package com.artie.baseball.domain

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded

/**
 * A game as its events fold. Who bats is counted, not named: each side's turns at bat are numbered 0, 1, 2…
 * through the game, and turn t is the batter at t modulo the lineup's length, so the order carries on from
 * inning to inning. A runner on a base is the turn that put them there. [setup] is the game's teams, set on the
 * folded state when it is read; [version] is the number of the last event.
 */
data class GameState(
    val id: Long,
    val setup: GameSetup? = null,
    val phase: Phase = Phase.SCHEDULED,
    val inning: Int = 0,
    val top: Boolean = true,
    val outs: Int = 0,
    val pitches: Int = 0,
    val bases: List<Int?> = EMPTY_BASES,
    val turns: Map<String, Int> = emptyMap(),
    val line: Map<String, List<Int>> = emptyMap(),
    val hits: Map<String, List<Int>> = emptyMap(),
    val runs: Map<String, List<Int>> = emptyMap(),
    val runsThisHalf: Int = 0,
    val version: Int = 0,
) {
    /** The side batting; a function, so the state's snapshot holds only what the events made. */
    fun batting(): String = if (top) Side.AWAY else Side.HOME

    /** The turn at bat now, for the side batting. */
    fun turn(side: String = batting()): Int = turns[side] ?: 0

    fun score(side: String): Int = line[side].orEmpty().sum()

    fun runsIn(side: String, inning: Int): Int = line[side].orEmpty().getOrElse(inning - 1) { 0 }

    /** Turn [turn]'s place in [side]'s lineup. */
    fun lineupIndex(side: String, turn: Int): Int = turn % checkNotNull(setup).team(side).lineup.size

    fun batterUp(): Player = checkNotNull(setup).team(batting()).lineup[lineupIndex(batting(), turn())]

    fun runner(base: String): Player? = bases[Bases.indexOf(base)]?.let {
        checkNotNull(setup).team(batting()).lineup[lineupIndex(batting(), it)]
    }

    /** How many of [side]'s turns, of [turns], were the player at [index] in the lineup. */
    fun countFor(side: String, index: Int, turns: Map<String, List<Int>>): Int =
        turns[side].orEmpty().count { lineupIndex(side, it) == index }

    companion object {
        val EMPTY_BASES: List<Int?> = listOf(null, null, null)
    }
}

/** The fold: one event at a time, in order. */
object GameProjector {
    private const val FIELDED_OUT = "fielded-out"
    private val HITS = listOf("single", "double", "triple", "home-run")

    fun apply(state: GameState, recorded: Recorded<GameEvent>): GameState =
        play(state, recorded.event).copy(version = recorded.sequenceNo)

    private fun play(s: GameState, e: GameEvent): GameState =
        when (e) {
            GameEvent.PlayBall -> s.copy(phase = Phase.PLAY, inning = 1, top = true)
            is GameEvent.Pitched -> pitched(s, e.result)
            is GameEvent.Hit -> if (e.result == FIELDED_OUT) out(s) else hit(s, e)
            is GameEvent.RunnerOut -> over(
                s.copy(outs = s.outs + 1, bases = s.bases.toMutableList().also { it[Bases.indexOf(e.base)] = null }),
            )
            GameEvent.NextHalf -> nextHalf(s)
            GameEvent.GameEnded -> s.copy(phase = Phase.FINAL)
        }

    /** A miss on the last pitch is out; a foul on it gives one more. */
    private fun pitched(s: GameState, result: String): GameState {
        val last = s.pitches + 1 >= Rules.PITCHES
        return when {
            !last -> s.copy(pitches = s.pitches + 1)
            result == "foul" -> s
            else -> out(s)
        }
    }

    /** The batter is out, and the next comes up. */
    private fun out(s: GameState): GameState = over(nextBatter(s).copy(outs = s.outs + 1))

    private fun hit(s: GameState, e: GameEvent.Hit): GameState {
        // A single moves everyone one base, a double two, a triple three, a home run all the way.
        val bases = HITS.indexOf(e.result) + 1
        val side = s.batting()
        val batter = s.turn()
        // Every runner, and the batter, moves up [bases]; whoever passes third scores.
        val moved = MutableList<Int?>(Bases.names.size) { null }
        val scored = mutableListOf<Int>()
        s.bases.forEachIndexed { at, runner -> runner?.let { advance(it, at + bases, moved, scored) } }
        advance(batter, bases - 1, moved, scored)
        val runsNow = s.runsThisHalf + scored.size
        val inningRuns = s.line[side].orEmpty().let { line ->
            line.toMutableList().also { while (it.size < s.inning) it.add(0) }
                .also { it[s.inning - 1] = it[s.inning - 1] + scored.size }
        }
        return over(
            nextBatter(s).copy(
                bases = moved,
                runsThisHalf = runsNow,
                line = s.line + (side to inningRuns),
                hits = s.hits + (side to s.hits[side].orEmpty() + batter),
                runs = s.runs + (side to s.runs[side].orEmpty() + scored),
            ),
        )
    }

    private fun advance(runner: Int, to: Int, moved: MutableList<Int?>, scored: MutableList<Int>) {
        if (to >= Bases.names.size) scored += runner else moved[to] = runner
    }

    private fun nextBatter(s: GameState): GameState =
        s.copy(pitches = 0, turns = s.turns + (s.batting() to s.turn() + 1))

    /** Three outs, or the run limit, end the half; the bottom of the last inning ends the innings. */
    private fun over(s: GameState): GameState {
        val limit = Rules.runLimit(s.inning)
        val ended = s.outs >= Rules.OUTS || (limit != null && s.runsThisHalf >= limit)
        return when {
            !ended -> s
            !s.top && s.inning >= Rules.INNINGS -> s.copy(phase = Phase.GAME_OVER)
            else -> s.copy(phase = Phase.HALF_OVER)
        }
    }

    private fun nextHalf(s: GameState): GameState =
        s.copy(
            phase = Phase.PLAY,
            top = !s.top,
            inning = if (s.top) s.inning else s.inning + 1,
            outs = 0,
            pitches = 0,
            bases = GameState.EMPTY_BASES,
            runsThisHalf = 0,
        )
}

/** How a game is event-sourced (heart-eventsourcing): its stream, its event types, and the fold. */
val GAMES: EventSourcing<GameEvent, GameState> = EventSourcing(
    stream = "game",
    types = mapOf(
        "PlayBall" to GameEvent.PlayBall::class.java,
        "Pitched" to GameEvent.Pitched::class.java,
        "Hit" to GameEvent.Hit::class.java,
        "RunnerOut" to GameEvent.RunnerOut::class.java,
        "NextHalf" to GameEvent.NextHalf::class.java,
        "GameEnded" to GameEvent.GameEnded::class.java,
    ),
    stateClass = GameState::class.java,
    empty = { id -> GameState(id) },
    apply = GameProjector::apply,
)
