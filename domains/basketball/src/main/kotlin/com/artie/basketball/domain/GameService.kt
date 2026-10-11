package com.artie.basketball.domain

import com.artie.heart.UnprocessableContentException
import com.artie.heart.Violation
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * Reads games and records what happens in them. Which acts are legal in a
 * phase is the declaration's business, checked before any of this runs; the
 * rules here are the ones a schema cannot say, such as whether a player is on
 * the team or has fouled out.
 */
class GameService(private val games: GameDirectory, private val events: EventStore) {

    suspend fun find(name: String?, id: Long?): List<GameSetup> =
        games.all()
            .filter { name == null || it.label.contains(name, ignoreCase = true) }
            .filter { id == null || it.id == id }
            .sortedBy { it.id }

    suspend fun state(id: Long, asOf: Instant? = null): GameState {
        val setup = games.find(id) ?: throw GameNotFoundException(id)
        return events.state(GAMES, id, asOf).copy(setup = setup)
    }

    suspend fun history(id: Long): List<Recorded<GameEvent>> {
        games.find(id) ?: throw GameNotFoundException(id)
        return events.history(GAMES, id)
    }

    /** Records the event [build] makes from the game as it is now, at the next number; returns the new state. */
    suspend fun record(id: Long, build: (GameState) -> GameEvent): GameState {
        val current = state(id)
        events.append(GAMES, id, current.version + 1, build(current))
        return state(id)
    }

    companion object {
        /** A player who may take part in a play: on the team's roster, and not fouled out. */
        fun requirePlayer(state: GameState, side: String, number: Int) {
            val roster = checkNotNull(state.setup).team(side).roster
            when {
                number !in roster -> refuse("/playerNumber", "No player $number on the $side team")
                state.fouledOut(side, number) -> refuse("/playerNumber", "Player $number has fouled out")
            }
        }

        /** A play that can be taken back: a scoring play not already taken back. */
        fun requireScoringPlay(state: GameState, sequenceNo: Int) {
            val play = state.plays.firstOrNull { it.sequenceNo == sequenceNo }
            when {
                play == null -> refuse("/sequenceNo", "Play $sequenceNo did not score")
                play.takenBack -> refuse("/sequenceNo", "Play $sequenceNo has already been taken back")
            }
        }

        private fun refuse(pointer: String, message: String): Nothing =
            throw UnprocessableContentException(listOf(Violation(pointer, message)))
    }
}
