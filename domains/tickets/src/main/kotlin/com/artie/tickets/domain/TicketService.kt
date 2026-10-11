package com.artie.tickets.domain

import com.artie.heart.UnprocessableContentException
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.Recorded
import java.time.Instant

/**
 * Opens tickets, reads them, and records what happens to them. Which acts
 * are legal in a status is the declaration's business, checked before any of
 * this runs; the rules here are the ones a schema cannot say, such as not
 * assigning a ticket to the agent who already has it.
 */
class TicketService(
    private val tickets: TicketDirectory,
    private val events: EventStore,
    private val atomically: Atomically = Atomically.DIRECT,
) {

    suspend fun find(subject: String?, id: Long?): List<TicketEntry> =
        tickets.all()
            .filter { subject == null || it.subject.contains(subject, ignoreCase = true) }
            .filter { id == null || it.id == id }
            .sortedBy { it.id }

    suspend fun state(id: Long, asOf: Instant? = null): TicketState {
        requireTicket(id)
        return events.state(TICKETS, id, asOf)
    }

    suspend fun history(id: Long): List<Recorded<TicketEvent>> {
        requireTicket(id)
        return events.history(TICKETS, id)
    }

    /** The ticket's directory entry; a ticket that was never opened is not found. */
    private suspend fun requireTicket(id: Long): TicketEntry =
        tickets.find(id) ?: throw TicketNotFoundException(id)

    /** Brings a ticket into being: its number and its opening, together; returns its state. */
    suspend fun open(opened: TicketEvent.Opened): TicketState {
        val id = atomically.run {
            tickets.open(opened.subject).also { events.append(TICKETS, it, 1, opened) }
        }
        return state(id)
    }

    /** Records the event [build] makes from the ticket as it is now, at the next number; returns the new state. */
    suspend fun record(id: Long, build: (TicketState) -> TicketEvent): TicketState {
        val current = state(id)
        events.append(TICKETS, id, current.version + 1, build(current))
        return state(id)
    }

    companion object {
        /** An agent to hand the ticket to: not the one who already has it. */
        fun requireOtherAgent(state: TicketState, agent: String) {
            if (state.agent == agent) {
                throw UnprocessableContentException.at("/agent", "The ticket is already assigned to $agent")
            }
        }

        /** A priority to change to: not the one the ticket already has. */
        fun requireOtherPriority(state: TicketState, priority: String) {
            if (state.priority == priority) {
                throw UnprocessableContentException.at("/priority", "The ticket's priority is already $priority")
            }
        }
    }
}
