package com.artie.heart.sims.library

import com.artie.heart.ConcurrentWriteException
import java.time.LocalDate
import java.util.concurrent.ConcurrentSkipListMap

/**
 * A book lent to a member: when it is [due], how many times it was renewed, whether someone else's hold is
 * waiting for it, and whether it came back. [version] changes with every act.
 */
data class Loan(
    val id: Long,
    val title: String,
    val author: String,
    val due: LocalDate,
    val renewals: Int = 0,
    val holdWaiting: Boolean = false,
    val returned: Boolean = false,
    val version: Int = 1,
) {
    /** What the loan can do now, as the library decides it: its offer key. */
    val standing: String
        get() = when {
            returned -> RETURNED
            holdWaiting -> ON_HOLD
            renewals >= MOST_RENEWALS -> AT_LIMIT
            else -> RENEWABLE
        }

    /** Renewed: due three weeks later, counted. */
    fun renewed(): Loan = copy(due = due.plusWeeks(RENEWAL_WEEKS), renewals = renewals + 1, version = version + 1)

    fun returnedNow(): Loan = copy(returned = true, version = version + 1)

    companion object {
        const val RENEWABLE = "renewable"
        const val ON_HOLD = "hold waiting"
        const val AT_LIMIT = "renewed the most allowed"
        const val RETURNED = "returned"

        const val MOST_RENEWALS = 3
        const val RENEWAL_WEEKS = 3L
    }
}

/** A member's account, by library card number: the books they have out now. */
data class Account(val card: Long, val name: String, val loans: List<Loan>) {
    /** Changes whenever one of its loans does, or a loan leaves it. */
    val version: Int get() = loans.sumOf { it.version } + loans.size
}

/** The loans of the library's one member, Ana Torres, kept once for every way the service answers. */
class Ledger {
    private val loans = ConcurrentSkipListMap<Long, Loan>()

    /** Back to the seed: The Hobbit with a hold waiting, Matilda, and Charlotte's Web renewed three times. */
    fun reset() = lend(SEED)

    /** Only [lent], in place of every loan there was. */
    fun lend(lent: List<Loan>) {
        loans.clear()
        lent.forEach { loans[it.id] = it }
    }

    fun get(id: Long): Loan? = loans[id]

    /** Every loan, in loan-number order. */
    fun all(): List<Loan> = loans.values.toList()

    /** Ana's account: the books she has out now. */
    fun account(): Account = Account(ANA_CARD, "Ana Torres", all().filterNot { it.returned })

    /** [next] in place of [current], only if nothing else changed the loan since; a stale write otherwise. */
    fun replace(current: Loan, next: Loan): Loan {
        if (!loans.replace(current.id, current, next)) {
            throw ConcurrentWriteException("loan ${current.id} changed since it was read")
        }
        return next
    }

    companion object {
        /** Ana Torres's library card number, which is her account's number too. */
        const val ANA_CARD = 21000451L

        private val DUE: LocalDate = LocalDate.parse("2026-11-12")

        val SEED = listOf(
            Loan(3310, "The Hobbit", "J. R. R. Tolkien", DUE, holdWaiting = true),
            Loan(3311, "Matilda", "Roald Dahl", DUE),
            Loan(3312, "Charlotte's Web", "E. B. White", DUE, renewals = Loan.MOST_RENEWALS),
        )
    }
}
