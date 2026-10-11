package com.artie.tickets.bdd

import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.tickets.domain.TICKETS
import com.artie.tickets.store.InMemoryTicketDirectory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/** A customer and an agent at the support desk, over HTTP through the HEART library. */
class TicketSteps(
    private val client: WebTestClient,
    private val tickets: InMemoryTicketDirectory,
    private val events: InMemoryEventStore,
) {
    private lateinit var last: EntityExchangeResult<ByteArray>
    private var state: JsonNode = ObjectMapper().createObjectNode()

    @Given("the support desk has no tickets")
    fun empty() = runBlocking {
        tickets.clear()
        events.forget(TICKETS.stream)
    }

    @When("a customer opens a ticket from the bookmark with {}")
    fun open(body: String) {
        val etag = index().responseHeaders.eTag
        last = client.post().uri("/actions/open-ticket")
            .header(HttpHeaders.ACCEPT, HAL_JSON).header(HttpHeaders.CONTENT_TYPE, JSON)
            .header(HttpHeaders.IF_MATCH, checkNotNull(etag))
            .bodyValue(body).exchange().expectBody().returnResult()
        if (last.status.is2xxSuccessful) state = json(last)
    }

    @Given("ticket {long} was opened about {string}")
    fun opened(id: Long, subject: String) {
        open(
            """{"subject": "$subject", "customer": "Dana Reyes", "description": "It is broken.", "priority": "high"}""",
        )
        assertEquals("/tickets/$id", last.responseHeaders.location?.toString())
    }

    @Given("ticket {long} is assigned to {string}")
    fun assigned(id: Long, agent: String) = act("assign", id, """{"agent": "$agent"}""")

    @Given("ticket {long} was resolved")
    fun resolved(id: Long) = act("resolve", id, """{"resolution": "Fixed."}""")

    @When("the agent records {string} on ticket {long} with {}")
    fun act(path: String, id: Long, body: String) {
        val etag = read(id).responseHeaders.eTag
        last = client.post().uri("/tickets/$id/actions/$path")
            .header(HttpHeaders.ACCEPT, HAL_JSON).header(HttpHeaders.CONTENT_TYPE, JSON)
            .header(HttpHeaders.IF_MATCH, checkNotNull(etag))
            .bodyValue(body).exchange().expectBody().returnResult()
        if (last.status.is2xxSuccessful) state = json(last)
    }

    @Then("ticket {long} is {string} with priority {string} and no agent")
    fun newTicket(id: Long, status: String, priority: String) {
        read(id)
        assertEquals(listOf(status, priority), listOf(state.path("status").asText(), state.path("priority").asText()))
        assertTrue(state.path("agent").isNull, state.toString())
    }

    @Then("ticket {long} is {string} to {string}")
    fun statusAndAgent(id: Long, status: String, agent: String) {
        read(id)
        assertEquals(listOf(status, agent), listOf(state.path("status").asText(), state.path("agent").asText()))
    }

    @Then("the conversation of ticket {long} is {string}")
    fun conversation(id: Long, expected: String) {
        read(id)
        val entries = state.path("conversation").map { entry ->
            val kind = entry.path("kind").asText()
            val label = if (kind == "comment" && entry.path("internal").asBoolean()) "internal comment" else kind
            "$label: ${entry.path("text").asText()}"
        }
        assertEquals(expected, entries.joinToString(" | "))
    }

    @Then("the ticket offers exactly {string}")
    fun offers(keys: String) {
        assertEquals(keys.split(",").map { it.trim() }.toSet(), forms())
    }

    @Then("the ticket offers nothing")
    fun offersNothing() = assertEquals(emptySet<String>(), forms())

    @Then("{string} is absent because {string}")
    fun absent(key: String, reason: String) {
        val href = state.path("_links").path("hr:unavailable").path("href").asText()
        val explanation = json(client.get().uri(href).exchange().expectBody().returnResult())
        val profile = state.path("_links").path("curies").first { it.path("name").asText() == key.substringBefore(':') }
        val rel = profile.path("href").asText().replace("{rel}", key.substringAfter(':'))
        val entry = explanation.path("unavailable").firstOrNull { it.path("rel").asText() == rel }
        assertEquals(reason, checkNotNull(entry) { "$key is not explained: $explanation" }.path("reason").asText())
    }

    @Then("ticket {long} has priority {string}")
    fun priority(id: Long, priority: String) {
        read(id)
        assertEquals(priority, state.path("priority").asText())
    }

    @Then("the desk refuses it as invalid at {string}")
    fun invalid(pointer: String) {
        assertEquals(422, last.status.value(), String(last.responseBody ?: ByteArray(0)))
        assertEquals(listOf(pointer), json(last).path("violations").map { it.path("pointer").asText() }.distinct())
    }

    private fun index(): EntityExchangeResult<ByteArray> =
        client.get().uri("/").header(HttpHeaders.ACCEPT, HAL_JSON).exchange().expectBody().returnResult()

    private fun read(id: Long): EntityExchangeResult<ByteArray> =
        client.get().uri("/tickets/$id").header(HttpHeaders.ACCEPT, HAL_JSON).exchange().expectBody().returnResult()
            .also { state = json(it) }

    private fun forms(): Set<String> = state.path("_forms").fieldNames().asSequence().toSet()

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private companion object {
        const val HAL_JSON = "application/hal+json"
        const val JSON = "application/json"
    }
}
