package com.artie.tickets.declaration

import com.artie.heart.Act
import com.artie.heart.Finding
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.tickets.domain.Status
import com.artie.tickets.domain.TicketState

/** A ticket, declared once for the HEART library. The service index offers opening one. */
object Tickets {

    val type = ResourceType(
        collection = "tickets",
        profile = SupportProfile.profile,
        acts = listOf(
            Act.of("assign", "assign", Assign::class.java),
            Act.of("comment", "comment", Comment::class.java),
            Act.of("askCustomer", "ask-customer", AskCustomer::class.java),
            Act.of("customerReplies", "customer-replies", CustomerReplies::class.java),
            Act.of("resolve", "resolve", Resolve::class.java),
            Act.of("reopen", "reopen", Reopen::class.java),
            Act.of("close", "close", NoInput::class.java),
            Act.of("changePriority", "change-priority", ChangePriority::class.java),
        ),
        bookmarkActs = listOf(Act.of("openTicket", "open-ticket", OpenTicket::class.java)),
        offers = Offers(
            keys = Status.entries,
            offered = { status ->
                when (status) {
                    Status.NEW -> listOf("assign", "comment", "changePriority")
                    Status.ASSIGNED -> listOf("assign", "comment", "askCustomer", "resolve", "changePriority")
                    Status.WAITING_ON_CUSTOMER -> listOf("comment", "customerReplies", "resolve", "changePriority")
                    Status.RESOLVED -> listOf("reopen", "close", "changePriority")
                    Status.CLOSED -> emptyList()
                }
            },
            whyAbsent = SupportProfile::whyAbsent,
        ),
        state = StateView<TicketState, Status>(
            keyOf = { it.status },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = ::body,
        ),
        finding = Finding(
            noun = "ticket",
            search = Named("searchTickets", "search-tickets"),
            lookup = Named("getTicket", "get-ticket"),
            nameDescription = "Part of the ticket's subject; case is ignored",
            idTitle = "Ticket number",
            idDescription = "The ticket's number",
        ),
    )

    val layers = Layers(type)

    private fun body(ticket: TicketState): Map<String, Any?> = linkedMapOf(
        "id" to ticket.id,
        "subject" to ticket.subject,
        "customer" to ticket.customer,
        "description" to ticket.description,
        "priority" to ticket.priority,
        "status" to ticket.status.name,
        "agent" to ticket.agent,
        "conversation" to ticket.conversation.map { turn ->
            linkedMapOf<String, Any?>("sequenceNo" to turn.sequenceNo, "kind" to turn.kind, "text" to turn.text)
                .apply { turn.internal?.let { put("internal", it) } }
                .apply { put("at", turn.at.toString()) }
        },
    )
}
