package com.artie.tolls.store

import com.artie.tolls.domain.AccountEvent
import com.artie.tolls.domain.AccountService
import com.artie.tolls.domain.Phase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/** The whole application on real Postgres: migration V1, the Postgres account directory, and the event store. */
@SpringBootTest(properties = ["heart.coap.port=-1"])
@Testcontainers(disabledWithoutDocker = true)
class PostgresAccountsTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var accounts: AccountService

    @Autowired
    lateinit var db: DatabaseClient

    @Test
    fun `an account set up in Postgres is funded and charged through the event store`() = runBlocking {
        db.sql("INSERT INTO account (id, holder) VALUES (1, 'Pat Miller')").await()

        val funded = accounts.record(accounts.state(1)) { AccountEvent.FundsAdded(2_500) }
        val after = accounts.record(funded) { AccountEvent.TripCharged("T-1001", "harbor-bridge", "car", "peak", 700) }

        assertEquals(Phase.ACTIVE, after.phase())
        assertEquals(1_800, after.balance)
        assertEquals(listOf(1, 2), accounts.history(1).map { it.sequenceNo })
    }
}
