package com.artie.gardenbed.config

import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.PostgresEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient

/**
 * The beds' events live in the HEART event store on Postgres, in the tables
 * migration V2 creates. The test suite supplies an in-memory store instead.
 */
@Configuration
class EventStoreConfig {
    @Bean
    fun eventStore(db: DatabaseClient, mapper: ObjectMapper): EventStore = PostgresEventStore(db, mapper)
}
