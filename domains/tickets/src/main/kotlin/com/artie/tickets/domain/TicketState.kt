package com.artie.tickets.domain

import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * One turn in a ticket's conversation: a comment ([internal] or for the
 * customer), a question to the customer, the customer's reply, a resolution,
 * or a reopening, with when it happened.
 */
data class Turn(val sequenceNo: Int, val kind: String, val text: String, val at: Instant, val internal: Boolean? = null)

/**
 * A ticket as its events fold. [version] is the number of the last event; a
 * ticket with no events has none of the opening's fields yet.
 */
data class TicketState(
    val id: Long,
    val subject: String = "",
    val customer: String = "",
    val description: String = "",
    val priority: String = "",
    val status: Status = Status.NEW,
    val agent: String? = null,
    val conversation: List<Turn> = emptyList(),
    val version: Int = 0,
)

/** The fold: one event at a time, in order. */
object TicketProjector {
    fun apply(state: TicketState, recorded: Recorded<TicketEvent>): TicketState =
        next(state, recorded).copy(version = recorded.sequenceNo)

    private fun next(s: TicketState, recorded: Recorded<TicketEvent>): TicketState {
        fun turn(kind: String, text: String, internal: Boolean? = null) =
            s.conversation + Turn(recorded.sequenceNo, kind, text, recorded.occurredAt, internal)
        return when (val e = recorded.event) {
            is TicketEvent.Opened ->
                s.copy(subject = e.subject, customer = e.customer, description = e.description, priority = e.priority)
            is TicketEvent.Assigned -> s.copy(status = Status.ASSIGNED, agent = e.agent)
            is TicketEvent.Commented -> s.copy(conversation = turn("comment", e.text, e.internal))
            is TicketEvent.CustomerAsked ->
                s.copy(status = Status.WAITING_ON_CUSTOMER, conversation = turn("question", e.question))
            is TicketEvent.CustomerReplied -> s.copy(status = Status.ASSIGNED, conversation = turn("reply", e.text))
            is TicketEvent.Resolved -> s.copy(status = Status.RESOLVED, conversation = turn("resolution", e.resolution))
            is TicketEvent.Reopened -> s.copy(status = Status.ASSIGNED, conversation = turn("reopened", e.reason))
            TicketEvent.Closed -> s.copy(status = Status.CLOSED)
            is TicketEvent.PriorityChanged -> s.copy(priority = e.priority)
        }
    }
}

/** How a ticket is event-sourced (heart-eventsourcing): its stream, its event types, and the fold. */
val TICKETS: EventSourcing<TicketEvent, TicketState> = EventSourcing(
    stream = "ticket",
    types = mapOf(
        "Opened" to TicketEvent.Opened::class.java,
        "Assigned" to TicketEvent.Assigned::class.java,
        "Commented" to TicketEvent.Commented::class.java,
        "CustomerAsked" to TicketEvent.CustomerAsked::class.java,
        "CustomerReplied" to TicketEvent.CustomerReplied::class.java,
        "Resolved" to TicketEvent.Resolved::class.java,
        "Reopened" to TicketEvent.Reopened::class.java,
        "Closed" to TicketEvent.Closed::class.java,
        "PriorityChanged" to TicketEvent.PriorityChanged::class.java,
    ),
    stateClass = TicketState::class.java,
    empty = { id -> TicketState(id) },
    apply = TicketProjector::apply,
)
