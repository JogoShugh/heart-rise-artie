package com.artie.tickets.tck

import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.tickets.bdd.TestCredentials
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import com.artie.tickets.bdd.InMemoryTickets
import com.artie.tickets.config.HeartConfig
import com.artie.tickets.domain.TICKETS
import com.artie.tickets.domain.TicketEvent
import com.artie.tickets.domain.TicketService
import com.artie.tickets.store.InMemoryTicketDirectory
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** Tickets run HEART's conformance features (heart-tck) against themselves, the bookmark's act included. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.tickets.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary")
class RunHeartTckTest

/** Ticket 1, new: assigning is offered, resolving is not; the bookmark offers opening a ticket. */
@TestConfiguration
class TicketFixture {
    @Bean
    fun ticketTckFixture(
        tickets: InMemoryTicketDirectory,
        events: InMemoryEventStore,
        service: TicketService,
    ): TckFixture = object : TckFixture {
        override val collection = "tickets"
        override val offered = TckAct(
            key = "support:assign",
            path = "assign",
            body = """{"agent": "priya"}""",
            invalidBody = """{"agent": "sam"}""",
            invalidPointer = "/agent",
        )
        override val absent = TckAct(
            key = "support:resolve",
            path = "resolve",
            body = """{"resolution": "Fixed."}""",
            reason = "No one is assigned; assign the ticket first.",
        )
        override val credential = TestCredentials.CREDENTIAL
        override val searchTerm = "log in"
        override val bookmarkAct = TckAct(
            key = "support:open-ticket",
            path = "open-ticket",
            body = opening("Every page."),
            invalidBody = opening(""),
            invalidPointer = "/description",
        )

        override suspend fun ready(): Long {
            tickets.clear()
            events.forget(TICKETS.stream)
            val opened = TicketEvent.Opened("Cannot log in", "Dana Reyes", "The button does nothing.", "high")
            return service.open(opened).id
        }

        private fun opening(description: String) =
            """{"subject": "Printer jams", "customer": "Lee Park", "description": "$description", "priority": "low"}"""
    }
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryTickets::class, TicketFixture::class, TckWorld::class,
    HeartConfig::class, HeartAutoConfiguration::class, HeartCoapAutoConfiguration::class,
    TestCredentials::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class TicketTckContext
