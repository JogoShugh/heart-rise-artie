package com.artie.tickets.declaration

import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.tickets.domain.Status

/** The support vocabulary (served as ALPS at `/profiles/support`), and why each act is absent in each status. */
object SupportProfile {

    val profile = Profile(
        name = "support",
        title = "Support tickets",
        doc = "What a support desk does with a customer's problem, from opening a ticket to closing it. " +
            "Only a ticket's current manifest says what may be done now.",
        relations = listOf(
            act("open-ticket", "Open a ticket", "Reports a customer's problem; the desk gives it a new ticket."),
            act("assign", "Assign", "Hands the ticket to an agent, who then works on it."),
            act("comment", "Comment", "Adds a note to the conversation: internal, or for the customer to read."),
            act("ask-customer", "Ask the customer", "Puts a question to the customer and waits for their reply."),
            act("customer-replies", "Customer replies", "Records the customer's answer; the agent works on again."),
            act("resolve", "Resolve", "Says how the problem was solved."),
            act("reopen", "Reopen", "Hands a resolved ticket back to its agent, because the problem is not solved."),
            act("close", "Close", "Ends a resolved ticket for good."),
            act("change-priority", "Change the priority", "Makes the ticket more or less urgent."),
            Relation("search-tickets", AlpsType.SAFE, "Find tickets by subject",
                "Lists the tickets whose subject has the text.",
                "A safe search; each result links to the ticket, titled with its subject."),
            Relation("get-ticket", AlpsType.SAFE, "Get a ticket by its number", "Goes to one ticket, given its number.",
                "A safe lookup; the result is the ticket, with what may be done now."),
        ),
    )

    private fun act(id: String, title: String, description: String) =
        Relation(id, AlpsType.UNSAFE, title, description, description)

    /** Why act [name] is absent in [status]. A function of the status alone. */
    fun whyAbsent(status: Status, name: String): String =
        when (status) {
            Status.NEW, Status.ASSIGNED -> when (name) {
                "askCustomer", "resolve" -> "No one is assigned; assign the ticket first."
                "customerReplies" -> "The customer has not been asked anything."
                else -> NOT_RESOLVED
            }
            Status.WAITING_ON_CUSTOMER -> when (name) {
                "reopen", "close" -> NOT_RESOLVED
                else -> "The customer has already been asked; wait for their reply."
            }
            Status.RESOLVED -> "The ticket is resolved; reopen it first."
            Status.CLOSED -> "The ticket is closed."
        }

    private const val NOT_RESOLVED = "The ticket is not resolved."
}
