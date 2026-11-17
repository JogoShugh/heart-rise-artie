package com.artie.tolls.domain

/**
 * What happened to an account, in order. The event store records which account, each event's number, and when.
 * A trip records the fare charged, so a later change to the fares never rewrites a past trip.
 */
sealed interface AccountEvent {
    data class FundsAdded(val cents: Long) : AccountEvent

    data class TagAdded(val tag: String) : AccountEvent

    data class TagLost(val tag: String) : AccountEvent

    data class TripCharged(
        val tag: String,
        val plaza: String,
        val vehicleClass: String,
        val time: String,
        val fareCents: Long,
    ) : AccountEvent

    data class TripRefunded(val sequenceNo: Int) : AccountEvent

    data class AccountClosed(val refundCents: Long) : AccountEvent
}
