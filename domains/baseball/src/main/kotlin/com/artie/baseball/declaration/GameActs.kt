package com.artie.baseball.declaration

import com.artie.baseball.domain.GameEvent
import com.artie.baseball.domain.GameService
import com.artie.baseball.domain.GameService.Companion.requireRunner
import com.artie.baseball.domain.GameState
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
            "playBall" -> GameEvent.PlayBall
            "pitch" -> GameEvent.Pitched(checkNotNull(input.bind(Pitch::class.java).result))
            "hit" -> GameEvent.Hit(checkNotNull(input.bind(Hit::class.java).result))
            "runnerOut" -> checkNotNull(input.bind(RunnerOut::class.java).base).let {
                requireRunner(game, it)
                GameEvent.RunnerOut(it)
            }
            "nextHalf" -> GameEvent.NextHalf
            "endGame" -> GameEvent.GameEnded
            else -> error("no act $act")
        }
}
