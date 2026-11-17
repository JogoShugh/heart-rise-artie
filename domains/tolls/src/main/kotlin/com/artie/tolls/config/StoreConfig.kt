package com.artie.tolls.config

import com.artie.heart.eventsourcing.PostgresEventStore
import com.artie.tolls.domain.AccountService
import com.artie.tolls.store.PostgresAccountDirectory
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient

/** Accounts and their events in Postgres. The test suite supplies in-memory ones instead. */
@Configuration
class StoreConfig {
    @Bean
    fun accountService(db: DatabaseClient, mapper: ObjectMapper) =
        AccountService(PostgresAccountDirectory(db), PostgresEventStore(db, mapper))
}
