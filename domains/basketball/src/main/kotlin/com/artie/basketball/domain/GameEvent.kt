package com.artie.basketball.domain

/**
 * What happened in a game, in order. The event store records which game,
 * each event's number in the game, and when; an event is only what happened.
 */
sealed interface GameEvent {
    data class TippedOff(val possession: String) : GameEvent

    data class BasketScored(val team: String, val playerNumber: Int, val points: Int) : GameEvent

    data class FreeThrowTaken(val team: String, val playerNumber: Int, val made: Boolean) : GameEvent

    data class FoulRecorded(val team: String, val playerNumber: Int, val kind: String) : GameEvent

    data class TurnoverRecorded(val team: String, val reason: String) : GameEvent

    data class TimeoutCalled(val team: String) : GameEvent

    data object PlayResumed : GameEvent

    data object PeriodEnded : GameEvent

    data object PeriodStarted : GameEvent

    data object FinalWhistle : GameEvent

    data class PlayTakenBack(val sequenceNo: Int) : GameEvent
}
