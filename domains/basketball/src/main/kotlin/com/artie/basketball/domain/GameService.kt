package com.artie.basketball.domain

import com.artie.heart.RuleRefusedException
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * Reads games and records what happens in them. Which acts are legal in a
 * phase is the declaration's business, checked before any of this runs; the
 * rules here are the ones a schema cannot say, such as whether a player is on
 * the team or has fouled out. Each refuses with [RuleRefusedException], a 422
 * of type rule-refused, naming the field.
 */
class GameService(private val games: GameDirectory, private val events: EventStore) {

    suspend fun find(name: String?, id: Long?): List<GameSetup> =
        games.all()
            .filter { name == null || it.label.contains(name, ignoreCase = true) }
            .filter { id == null || it.id == id }
            .sortedBy { it.id }

    suspend fun state(id: Long, asOf: Instant? = null): GameState {
        val setup = requireGame(id)
        return events.state(GAMES, id, asOf).copy(setup = setup)
    }

    suspend fun history(id: Long): List<Recorded<GameEvent>> {
        requireGame(id)
        return events.history(GAMES, id)
    }

    /** The game's setup; a game that was never set up is not found. */
    private suspend fun requireGame(id: Long): GameSetup =
        games.find(id) ?: throw GameNotFoundException(id)

    /**
     * Records the event [build] makes from [current], at the number after it;
     * returns the new state. If another event took that number first, the
     * game has moved past [current], and the store refuses it
     * ([com.artie.heart.ConcurrentWriteException]).
     */
    suspend fun record(current: GameState, build: (GameState) -> GameEvent): GameState {
        events.append(GAMES, current.id, current.version + 1, build(current))
        return state(current.id)
    }

    companion object {
        /** A player who may take part in a play: on the team's roster, and not fouled out. */
        fun requirePlayer(state: GameState, side: String, number: Int) {
            val roster = checkNotNull(state.setup).team(side).roster
            when {
                number !in roster ->
                    throw RuleRefusedException.at("/playerNumber", "No player $number on the $side team")
                state.fouledOut(side, number) ->
                    throw RuleRefusedException.at("/playerNumber", "Player $number has fouled out")
            }
        }

        /** A play that can be taken back: a scoring play not already taken back. */
        fun requireScoringPlay(state: GameState, sequenceNo: Int) {
            val play = state.plays.firstOrNull { it.sequenceNo == sequenceNo }
            when {
                play == null ->
                    throw RuleRefusedException.at("/sequenceNo", "Play $sequenceNo did not score")
                play.takenBack -> throw RuleRefusedException.at(
                    "/sequenceNo",
                    "Play $sequenceNo has already been taken back",
                )
            }
        }
    }
}
