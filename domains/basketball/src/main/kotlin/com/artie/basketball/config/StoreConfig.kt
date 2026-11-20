package com.artie.basketball.config

import com.artie.basketball.domain.GameService
import com.artie.basketball.store.PostgresGameDirectory
import com.artie.heart.eventsourcing.PostgresEventStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient

/** Games and their events in Postgres. The test suite supplies in-memory ones instead. */
@Configuration
class StoreConfig {
    @Bean
    fun gameService(db: DatabaseClient, mapper: ObjectMapper) =
        GameService(PostgresGameDirectory(db), PostgresEventStore(db, mapper))
}
