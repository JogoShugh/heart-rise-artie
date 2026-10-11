package com.artie.tickets.store

import com.artie.tickets.domain.Status
import com.artie.tickets.domain.TicketEvent
import com.artie.tickets.domain.TicketService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The whole application on real Postgres: migration V1, the Postgres ticket
 * directory, the transaction around a new ticket, and the library's Postgres
 * event store.
 */
@SpringBootTest(properties = ["heart.coap.port=-1"])
@Testcontainers(disabledWithoutDocker = true)
class PostgresTicketsTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var tickets: TicketService

    @Test
    fun `a ticket opened in Postgres is worked through the event store`() = runBlocking {
        val opened = tickets.open(TicketEvent.Opened("Cannot log in", "Dana Reyes", "Nothing happens.", "high"))
        tickets.record(opened.id) { TicketEvent.Assigned("priya") }
        val after = tickets.record(opened.id) { TicketEvent.CustomerAsked("Which browser?") }

        assertEquals(Status.WAITING_ON_CUSTOMER, after.status)
        assertEquals("priya", after.agent)
        assertEquals(listOf(opened.id), tickets.find("log in", null).map { it.id })
        assertEquals(listOf(1, 2, 3), tickets.history(opened.id).map { it.sequenceNo })
    }
}
