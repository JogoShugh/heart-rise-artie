package com.artie.tolls.domain

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded

/** A transponder on the account. A lost tag stays listed, and no longer pays. */
data class Tag(val id: String, val lost: Boolean = false)

/** One line of the statement: a payment in, or a trip charged, which can be disputed once. */
data class Entry(
    val sequenceNo: Int,
    val kind: String,
    val cents: Long,
    val plaza: String? = null,
    val vehicleClass: String? = null,
    val time: String? = null,
    val tag: String? = null,
    val refunded: Boolean = false,
)

/**
 * An account as its events fold: the balance, the tags, the statement. [setup] is who holds it, set on the folded
 * state when it is read; [version] is the number of the last event. The phase follows from these, so it is a
 * function: the state's snapshot holds only what the events made.
 */
data class AccountState(
    val id: Long,
    val setup: AccountSetup? = null,
    val balance: Long = 0,
    val opened: Boolean = false,
    val closed: Boolean = false,
    val refunded: Long = 0,
    val tags: List<Tag> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val version: Int = 0,
) {
    fun phase(): Phase = when {
        closed -> Phase.CLOSED
        !opened -> Phase.NEW
        balance < 0 -> Phase.SUSPENDED
        balance < Rules.LOW_BALANCE -> Phase.LOW_BALANCE
        else -> Phase.ACTIVE
    }

    fun tag(id: String): Tag? = tags.firstOrNull { it.id == id }
}

/** The fold: one event at a time, in order. */
object AccountProjector {
    fun apply(state: AccountState, recorded: Recorded<AccountEvent>): AccountState =
        play(state, recorded.sequenceNo, recorded.event).copy(version = recorded.sequenceNo)

    private fun play(s: AccountState, sequenceNo: Int, e: AccountEvent): AccountState =
        when (e) {
            is AccountEvent.FundsAdded -> s.copy(
                balance = s.balance + e.cents,
                opened = true,
                entries = s.entries + Entry(sequenceNo, "payment", e.cents),
            )
            is AccountEvent.TagAdded -> s.copy(tags = s.tags + Tag(e.tag))
            is AccountEvent.TagLost -> s.copy(tags = s.tags.map { if (it.id == e.tag) it.copy(lost = true) else it })
            is AccountEvent.TripCharged -> s.copy(
                balance = s.balance - e.fareCents,
                entries = s.entries + Entry(sequenceNo, "trip", -e.fareCents, e.plaza, e.vehicleClass, e.time, e.tag),
            )
            is AccountEvent.TripRefunded -> refunded(s, e.sequenceNo)
            is AccountEvent.AccountClosed -> s.copy(closed = true, refunded = e.refundCents, balance = 0)
        }

    private fun refunded(s: AccountState, sequenceNo: Int): AccountState {
        val trip = s.entries.first { it.sequenceNo == sequenceNo }
        return s.copy(
            balance = s.balance - trip.cents,
            entries = s.entries.map { if (it.sequenceNo == sequenceNo) it.copy(refunded = true) else it },
        )
    }
}

/** How an account is event-sourced (heart-eventsourcing): its stream, its event types, and the fold. */
val ACCOUNTS: EventSourcing<AccountEvent, AccountState> = EventSourcing(
    stream = "account",
    types = mapOf(
        "FundsAdded" to AccountEvent.FundsAdded::class.java,
        "TagAdded" to AccountEvent.TagAdded::class.java,
        "TagLost" to AccountEvent.TagLost::class.java,
        "TripCharged" to AccountEvent.TripCharged::class.java,
        "TripRefunded" to AccountEvent.TripRefunded::class.java,
        "AccountClosed" to AccountEvent.AccountClosed::class.java,
    ),
    stateClass = AccountState::class.java,
    empty = { id -> AccountState(id) },
    apply = AccountProjector::apply,
)
