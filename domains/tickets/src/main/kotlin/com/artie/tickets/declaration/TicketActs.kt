package com.artie.tickets.declaration

import com.artie.heart.ActInput
import com.artie.tickets.domain.TicketEvent
import com.artie.tickets.domain.TicketService
import com.artie.tickets.domain.TicketService.Companion.requireOtherAgent
import com.artie.tickets.domain.TicketService.Companion.requireOtherPriority
import com.artie.tickets.domain.TicketState

/**
 * What each act records. The HEART library has already checked that the act
 * is offered and that its body satisfies the act's schema.
 */
class TicketActs(private val tickets: TicketService) {

    /** The bookmark's act: a customer's problem becomes a new ticket. */
    suspend fun open(input: ActInput): TicketState =
        input.bind(OpenTicket::class.java).let {
            tickets.open(TicketEvent.Opened(it.subject!!, it.customer!!, it.description!!, it.priority!!))
        }

    suspend fun perform(id: Long, act: String, input: ActInput): TicketState =
        tickets.record(id) { ticket -> event(act, input, ticket) }

    private fun event(act: String, input: ActInput, ticket: TicketState): TicketEvent =
        when (act) {
            "assign" -> checkNotNull(input.bind(Assign::class.java).agent).let {
                requireOtherAgent(ticket, it)
                TicketEvent.Assigned(it)
            }
            "comment" -> input.bind(Comment::class.java).let { TicketEvent.Commented(it.text!!, it.internal!!) }
            "askCustomer" -> TicketEvent.CustomerAsked(checkNotNull(input.bind(AskCustomer::class.java).question))
            "customerReplies" -> TicketEvent.CustomerReplied(checkNotNull(input.bind(CustomerReplies::class.java).text))
            "resolve" -> TicketEvent.Resolved(checkNotNull(input.bind(Resolve::class.java).resolution))
            "reopen" -> TicketEvent.Reopened(checkNotNull(input.bind(Reopen::class.java).reason))
            "close" -> TicketEvent.Closed
            "changePriority" -> checkNotNull(input.bind(ChangePriority::class.java).priority).let {
                requireOtherPriority(ticket, it)
                TicketEvent.PriorityChanged(it)
            }
            else -> error("no act $act")
        }
}
