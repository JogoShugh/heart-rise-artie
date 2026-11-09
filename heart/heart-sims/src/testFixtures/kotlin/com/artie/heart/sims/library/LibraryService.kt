package com.artie.heart.sims.library

import org.http4k.core.HttpHandler

/**
 * Riverside Public Library's loans, served as a HEART service by heart-core ([HeartLibrary]). A reset lends Ana
 * Torres the seed's three books again; [lend] lends her others.
 */
class LibraryService {
    private val ledger = Ledger()
    private val heart = HeartLibrary(ledger)

    init {
        reset()
    }

    fun reset() = ledger.reset()

    /** Ana's loans are [loans] from now on, until a reset. */
    fun lend(loans: List<Loan>) = ledger.lend(loans)

    /** The hold on loan [id] has been returned: nothing is waiting for the book any more. */
    fun releaseHold(id: Long) = ledger.releaseHold(id)

    /** The loan [id] as it is now; null when there is none. */
    fun loan(id: Long): Loan? = ledger.get(id)

    val handler: HttpHandler = heart.handler
}
