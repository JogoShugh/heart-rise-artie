package com.artie.tickets.domain

/**
 * What happened to a ticket, in order. The event store records which ticket,
 * each event's number in the ticket, and when; an event is only what happened.
 */
sealed interface TicketEvent {
    data class Opened(val subject: String, val customer: String, val description: String, val priority: String) :
        TicketEvent

    data class Assigned(val agent: String) : TicketEvent

    data class Commented(val text: String, val internal: Boolean) : TicketEvent

    data class CustomerAsked(val question: String) : TicketEvent

    data class CustomerReplied(val text: String) : TicketEvent

    data class Resolved(val resolution: String) : TicketEvent

    data class Reopened(val reason: String) : TicketEvent

    data object Closed : TicketEvent

    data class PriorityChanged(val priority: String) : TicketEvent
}
