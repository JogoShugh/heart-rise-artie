package com.artie.basketball.declaration

import com.artie.basketball.domain.GameEvent
import com.artie.basketball.domain.GameService
import com.artie.basketball.domain.GameService.Companion.requirePlayer
import com.artie.basketball.domain.GameService.Companion.requireScoringPlay
import com.artie.basketball.domain.GameState
import com.artie.heart.ActInput

/**
 * What each act records. The HEART library has already checked that the act
 * is offered and that its body satisfies the act's schema.
 */
class GameActs(private val games: GameService) {

    /** Act [act] on [game], the state the client's validator was checked against. */
    suspend fun perform(game: GameState, act: String, input: ActInput): GameState =
        games.record(game) { event(act, input, it) }

    private fun event(act: String, input: ActInput, game: GameState): GameEvent =
        when (act) {
            "scoreBasket", "freeThrow", "recordFoul" -> playerEvent(act, input, game)
            "tipOff" -> GameEvent.TippedOff(checkNotNull(input.bind(TipOff::class.java).possession))
            "turnover" -> input.bind(Turnover::class.java).let { GameEvent.TurnoverRecorded(it.team!!, it.reason!!) }
            "callTimeout" -> GameEvent.TimeoutCalled(checkNotNull(input.bind(CallTimeout::class.java).team))
            "resumePlay" -> GameEvent.PlayResumed
            "endPeriod" -> GameEvent.PeriodEnded
            "startPeriod" -> GameEvent.PeriodStarted
            "finalWhistle" -> GameEvent.FinalWhistle
            "correctScore" -> checkNotNull(input.bind(CorrectScore::class.java).sequenceNo).let {
                requireScoringPlay(game, it)
                GameEvent.PlayTakenBack(it)
            }
            else -> error("no act $act")
        }

    /** A play by one player, who must be on the team and not fouled out. */
    private fun playerEvent(act: String, input: ActInput, game: GameState): GameEvent =
        when (act) {
            "scoreBasket" -> input.bind(ScoreBasket::class.java).let {
                requirePlayer(game, it.team!!, it.playerNumber!!)
                GameEvent.BasketScored(it.team, it.playerNumber, it.points!!)
            }
            "freeThrow" -> input.bind(FreeThrow::class.java).let {
                requirePlayer(game, it.team!!, it.playerNumber!!)
                GameEvent.FreeThrowTaken(it.team, it.playerNumber, it.made!!)
            }
            "recordFoul" -> input.bind(RecordFoul::class.java).let {
                requirePlayer(game, it.team!!, it.playerNumber!!)
                GameEvent.FoulRecorded(it.team, it.playerNumber, it.kind!!)
            }
            else -> error("no player act $act")
        }
}
