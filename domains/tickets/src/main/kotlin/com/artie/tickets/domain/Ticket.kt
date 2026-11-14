package com.artie.tickets.domain

import com.artie.heart.ResourceNotFoundException

/** Where a ticket is: which acts may be done on it depends on this alone. */
enum class Status { NEW, ASSIGNED, WAITING_ON_CUSTOMER, RESOLVED, CLOSED }

/** A ticket's number and its subject, as the desk lists them. */
data class TicketEntry(val id: Long, val subject: String)

/** 404: no ticket with that id. */
class TicketNotFoundException(id: Long) : ResourceNotFoundException("No ticket with id $id")

/**
 * The tickets that have been opened: each one's number and subject, so a
 * ticket can be found by its subject. What happens to a ticket is in its
 * events, not here.
 */
interface TicketDirectory {
    /** Gives a new ticket about [subject] its number. */
    suspend fun open(subject: String): Long

    suspend fun find(id: Long): TicketEntry?

    suspend fun all(): List<TicketEntry>
}

/**
 * Runs a block as one unit of work: a ticket's number and its first event are
 * stored together or not at all. A store that is already all-or-nothing runs
 * the block as it is ([DIRECT]).
 */
interface Atomically {
    suspend fun <T> run(block: suspend () -> T): T

    companion object {
        val DIRECT: Atomically = object : Atomically {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }
    }
}
