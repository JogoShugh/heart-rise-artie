package com.artie.tolls.declaration

import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.tolls.domain.Phase

/** The toll vocabulary (served as ALPS at `/profiles/tolls`), and why each act is absent in each phase. */
object TollsProfile {

    val profile = Profile(
        name = "tolls",
        title = "Toll accounts and fares",
        doc = "A prepaid toll account, E-ZPass style: funds, tags, trips charged at plazas, disputes. Only an " +
            "account's current manifest says what may be done now.",
        relations = listOf(
            act("add-funds", "Add funds", "Adds whole dollars to the balance; an account opens with at least $25."),
            act("add-tag", "Add a tag", "Puts another transponder on the account."),
            act("record-trip", "Charge a trip", "A plaza charges a trip to one of the account's tags."),
            act("report-tag-lost", "Report a tag lost", "The tag no longer pays."),
            act("dispute-toll", "Dispute a toll", "Refunds a trip charged by mistake, once."),
            act("close-account", "Close the account", "Refunds the balance and closes the account."),
            Relation(
                "search-accounts",
                AlpsType.SAFE,
                "Find accounts by holder",
                "Lists the accounts whose holder's name has the text.",
                "A safe search; each result links to the account, titled with its holder.",
            ),
            Relation(
                "get-account",
                AlpsType.SAFE,
                "Get an account by its number",
                "Goes to one account, given its number.",
                "A safe lookup; the result is the account, with what may be done now.",
            ),
        ),
    )

    private fun act(id: String, title: String, description: String) =
        Relation(id, AlpsType.UNSAFE, title, description, description)

    /** Why act [name] is absent in [phase]. A function of the phase alone. */
    fun whyAbsent(phase: Phase, @Suppress("UNUSED_PARAMETER") name: String): String =
        when (phase) {
            Phase.NEW -> "The account is not open yet; add at least $25 first."
            Phase.SUSPENDED -> "The balance is below zero, so the tags no longer pay; add funds first."
            Phase.CLOSED -> "The account is closed."
            Phase.ACTIVE, Phase.LOW_BALANCE -> "Every act is offered on an open account."
        }
}
