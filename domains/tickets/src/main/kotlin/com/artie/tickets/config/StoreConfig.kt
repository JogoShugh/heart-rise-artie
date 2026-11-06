package com.artie.tickets.config

import com.artie.heart.eventsourcing.PostgresEventStore
import com.artie.tickets.domain.TicketService
import com.artie.tickets.store.PostgresAtomically
import com.artie.tickets.store.PostgresTicketDirectory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator

/** Tickets and their events in Postgres, a new ticket in one transaction. The test suite supplies in-memory ones. */
@Configuration
class StoreConfig {
    @Bean
    fun ticketService(db: DatabaseClient, mapper: ObjectMapper, transactions: TransactionalOperator) =
        TicketService(PostgresTicketDirectory(db), PostgresEventStore(db, mapper), PostgresAtomically(transactions))
}
