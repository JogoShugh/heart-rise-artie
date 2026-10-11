package com.artie.tickets.store

import com.artie.tickets.domain.Atomically
import com.artie.tickets.domain.TicketDirectory
import com.artie.tickets.domain.TicketEntry
import kotlinx.coroutines.flow.toList
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingle
import org.springframework.r2dbc.core.flow
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Tickets in memory: for tests and demos. Numbers start at 1 again after [clear]. */
class InMemoryTicketDirectory : TicketDirectory {
    private val tickets = ConcurrentHashMap<Long, TicketEntry>()
    private val last = AtomicLong()

    fun clear() {
        tickets.clear()
        last.set(0)
    }

    override suspend fun open(subject: String): Long =
        last.incrementAndGet().also { tickets[it] = TicketEntry(it, subject) }

    override suspend fun find(id: Long): TicketEntry? = tickets[id]

    override suspend fun all(): List<TicketEntry> = tickets.values.toList()
}

/** Tickets in Postgres: table `ticket` (migration V1), numbered by its sequence. */
class PostgresTicketDirectory(private val db: DatabaseClient) : TicketDirectory {

    override suspend fun open(subject: String): Long =
        db.sql("INSERT INTO ticket (subject) VALUES (:subject) RETURNING id")
            .bind("subject", subject)
            .map { row -> long(row.get("id")) }
            .awaitSingle()

    override suspend fun find(id: Long): TicketEntry? = all().firstOrNull { it.id == id }

    override suspend fun all(): List<TicketEntry> =
        db.sql("SELECT id, subject FROM ticket ORDER BY id")
            .map { row -> TicketEntry(long(row.get("id")), row.get("subject") as String) }
            .flow().toList()

    private companion object {
        fun long(value: Any?): Long = (value as Number).toLong()
    }
}

/** One Postgres transaction around the block: the ticket's row and its first event commit together. */
class PostgresAtomically(private val transactions: TransactionalOperator) : Atomically {
    override suspend fun <T> run(block: suspend () -> T): T = transactions.executeAndAwait { block() }
}
