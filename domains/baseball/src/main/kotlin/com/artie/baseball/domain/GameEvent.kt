package com.artie.baseball.domain

/**
 * What happened in a game, in order. The event store records which game,
 * each event's number in the game, and when; an event is only what happened.
 * Who batted and who ran follows from the order of play, so no event names a player.
 */
sealed interface GameEvent {
    data object PlayBall : GameEvent

    /** A pitch not hit into play: "missed" (swung at or taken) or "foul". */
    data class Pitched(val result: String) : GameEvent

    /** The ball put in play: single, double, triple, home-run, or fielded-out. */
    data class Hit(val result: String) : GameEvent

    /** A runner put out on the bases: first, second or third. */
    data class RunnerOut(val base: String) : GameEvent

    data object NextHalf : GameEvent

    data object GameEnded : GameEvent
}
