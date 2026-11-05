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
    /** The member it is lent to, by library card number. */
    val card: Long = Ledger.ANA_CARD,
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

/** A member of the library, by library card number. */
data class Member(val card: Long, val name: String)

/** The library's members and their loans, kept once for every way the service answers. */
class Ledger {
    private val members = ConcurrentSkipListMap<Long, Member>()
    private val loans = ConcurrentSkipListMap<Long, Loan>()

    /** Back to the seed: five members, Ana Torres's three books among their loans. */
    fun reset() {
        members.clear()
        MEMBERS.forEach { members[it.card] = it }
        lend(SEED)
    }

    /** Only [lent], in place of every loan there was. */
    fun lend(lent: List<Loan>) {
        loans.clear()
        lent.forEach { loans[it.id] = it }
    }

    fun get(id: Long): Loan? = loans[id]

    /** Every loan, in loan-number order. */
    fun all(): List<Loan> = loans.values.toList()

    /** A member's account: the books they have out now. */
    fun account(card: Long): Account? = members[card]?.let { member ->
        Account(member.card, member.name, all().filter { it.card == card && !it.returned })
    }

    /** Every member's account, in card-number order. */
    fun accounts(): List<Account> = members.keys.mapNotNull(::account)

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
        private const val MILLER = 21000452L
        private const val OKAFOR = 21000453L
        private const val CHEN = 21000454L
        private const val ROSSI = 21000455L

        val MEMBERS = listOf(
            Member(ANA_CARD, "Ana Torres"),
            Member(MILLER, "David Miller"),
            Member(OKAFOR, "Grace Okafor"),
            Member(CHEN, "Liam Chen"),
            Member(ROSSI, "Sofia Rossi"),
        )

        /** Each member's books, in every standing: renewable, a hold waiting, renewed the most allowed. */
        val SEED = listOf(
            Loan(3310, "The Hobbit", "J. R. R. Tolkien", DUE, holdWaiting = true),
            Loan(3311, "Matilda", "Roald Dahl", DUE),
            Loan(3312, "Charlotte's Web", "E. B. White", DUE, renewals = Loan.MOST_RENEWALS),
            Loan(3313, "The Secret Garden", "Frances Hodgson Burnett", DUE, card = MILLER),
            Loan(3314, "Holes", "Louis Sachar", DUE, renewals = 1, card = MILLER),
            Loan(3315, "Wonder", "R. J. Palacio", DUE, holdWaiting = true, card = OKAFOR),
            Loan(3316, "Hatchet", "Gary Paulsen", DUE, card = OKAFOR),
            Loan(3317, "Coraline", "Neil Gaiman", DUE, renewals = Loan.MOST_RENEWALS, card = CHEN),
            Loan(3318, "The Giver", "Lois Lowry", DUE, card = ROSSI),
            Loan(3319, "Frindle", "Andrew Clements", DUE, holdWaiting = true, card = ROSSI),
            Loan(3320, "Number the Stars", "Lois Lowry", DUE, renewals = 2, card = ROSSI),
        )
    }
}
