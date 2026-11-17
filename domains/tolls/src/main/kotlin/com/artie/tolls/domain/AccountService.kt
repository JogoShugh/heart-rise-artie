package com.artie.tolls.domain

import com.artie.heart.RuleRefusedException
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * Reads accounts and records what happens to them. Which acts are legal in a phase is the declaration's business,
 * checked before any of this runs; the rules here are the ones a schema cannot say, such as whether a tag is on the
 * account already. Each refuses with [RuleRefusedException], a 422 of type rule-refused, naming the field.
 */
class AccountService(private val accounts: AccountDirectory, private val events: EventStore) {

    suspend fun find(name: String?, id: Long?): List<AccountSetup> =
        accounts.all()
            .filter { name == null || it.holder.contains(name, ignoreCase = true) }
            .filter { id == null || it.id == id }
            .sortedBy { it.id }

    suspend fun state(id: Long, asOf: Instant? = null): AccountState {
        val setup = accounts.find(id) ?: throw AccountNotFoundException(id)
        return events.state(ACCOUNTS, id, asOf).copy(setup = setup)
    }

    suspend fun history(id: Long): List<Recorded<AccountEvent>> {
        accounts.find(id) ?: throw AccountNotFoundException(id)
        return events.history(ACCOUNTS, id)
    }

    /**
     * Records the event [build] makes from [current], at the number after it; returns the new state. If another
     * event took that number first, the account has moved past [current], and the store refuses it
     * ([com.artie.heart.ConcurrentWriteException]).
     */
    suspend fun record(current: AccountState, build: (AccountState) -> AccountEvent): AccountState {
        events.append(ACCOUNTS, current.id, current.version + 1, build(current))
        return state(current.id)
    }

    companion object {
        /** An account opens with at least the opening deposit. */
        fun requireDeposit(state: AccountState, cents: Long) {
            if (!state.opened && cents < Rules.OPENING_DEPOSIT) {
                throw RuleRefusedException.at(
                    "/amount",
                    "An account opens with at least $${Rules.dollars(Rules.OPENING_DEPOSIT)}",
                )
            }
        }

        /** A tag is on an account once. */
        fun requireNewTag(state: AccountState, tag: String) {
            if (state.tag(tag) != null) throw RuleRefusedException.at("/tag", "Tag $tag is already on the account")
        }

        /** A trip that can be disputed: a trip not already refunded. */
        fun requireTrip(state: AccountState, sequenceNo: Int) {
            val entry = state.entries.firstOrNull { it.sequenceNo == sequenceNo && it.kind == "trip" }
            when {
                entry == null -> throw RuleRefusedException.at("/sequenceNo", "Entry $sequenceNo is not a trip")
                entry.refunded -> throw RuleRefusedException.at(
                    "/sequenceNo",
                    "Trip $sequenceNo has already been refunded",
                )
            }
        }
    }
}
