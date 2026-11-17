package com.artie.tolls.bdd

import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.tolls.domain.ACCOUNTS
import com.artie.tolls.domain.AccountEvent
import com.artie.tolls.domain.AccountSetup
import com.artie.tolls.domain.Rules
import com.artie.tolls.store.InMemoryAccountDirectory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/** The holder's and the plaza's view of an account, over HTTP through the HEART library. */
class AccountSteps(
    private val client: WebTestClient,
    private val accounts: InMemoryAccountDirectory,
    private val events: InMemoryEventStore,
) {
    private lateinit var last: EntityExchangeResult<ByteArray>
    private var state: JsonNode = ObjectMapper().createObjectNode()

    @Given("account {long}, held by Pat Miller")
    fun anAccount(id: Long) = runBlocking {
        accounts.clear()
        events.forget(ACCOUNTS.stream)
        accounts.add(AccountSetup(id, "Pat Miller"))
    }

    @Given("account {long} is open with \${int} and the tag {string}")
    fun open(id: Long, dollars: Int, tag: String) = openWithTags(id, dollars, listOf(tag))

    @Given("account {long} is open with \${int} and the tags {string} and {string}")
    fun openWithTwo(id: Long, dollars: Int, first: String, second: String) =
        openWithTags(id, dollars, listOf(first, second))

    @Given("the tag {string} was reported lost")
    fun lost(tag: String) = runBlocking { record(1, AccountEvent.TagLost(tag)) }

    @Given("the tag {string} was charged {string} trips at {string} at {string} {int} time(s)")
    fun charged(tag: String, vehicleClass: String, plaza: String, time: String, count: Int) = runBlocking {
        repeat(count) {
            record(1, AccountEvent.TripCharged(tag, plaza, vehicleClass, time, Rules.fare(plaza, vehicleClass, time)))
        }
    }

    @When("the holder opens account {long}")
    fun read(id: Long) {
        get(id)
    }

    @Given("the plaza recorded {string} on account {long} with {}")
    @When("the holder records {string} on account {long} with {}")
    fun act(path: String, id: Long, body: String) {
        val etag = get(id).responseHeaders.eTag
        last = client.post().uri("/accounts/$id/actions/$path")
            .header(HttpHeaders.ACCEPT, HAL_JSON).header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.IF_MATCH, checkNotNull(etag))
            .bodyValue(body).exchange().expectBody().returnResult()
        if (last.status.is2xxSuccessful) state = json(last)
    }

    @When("the plaza records {string} on account {long} with {}")
    fun plaza(path: String, id: Long, body: String) = act(path, id, body)

    @When("the holder disputes account {long}'s entry number {int}")
    fun dispute(id: Long, sequenceNo: Int) = act("dispute-toll", id, """{"sequenceNo": $sequenceNo}""")

    @Then("the account offers exactly")
    fun offers(table: DataTable) = assertEquals(table.asMaps().map { it.getValue("act") }.toSet(), forms())

    @Then("the account offers nothing")
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

    @Then("account {long} is {string} with a balance of {string}")
    fun phase(id: Long, phase: String, balance: String) {
        get(id)
        assertEquals(phase to balance, state.path("phase").asText() to state.path("balance").asText())
    }

    @Then("the trip is charged {string}")
    fun charged(fare: String) {
        get(1)
        val trip = state.path("statement").last { it.path("kind").asText() == "trip" }
        assertEquals("-$fare", trip.path("amount").asText(), trip.toString())
    }

    @Then("the account says {string} was refunded")
    fun refunded(amount: String) {
        get(1)
        assertEquals(amount, state.path("refunded").asText())
    }

    @Then("the form {string} takes {string} from {string}, the {string} of each tag not lost")
    fun takesFrom(form: String, property: String, pointer: String, valueField: String) {
        val options = state.path("_forms").path(form).path("schema").path("properties").path(property)
            .path("hr:options")
        assertEquals(pointer, options.path("pointer").asText(), "$form: $options")
        assertEquals(valueField, options.path("valueField").asText(), "$form: $options")
        assertEquals(false, options.path("where").path("lost").asBoolean(true), "$form: $options")
    }

    @Then("the account refuses it as not one of the choices at {string}")
    fun notAChoice(pointer: String) {
        assertEquals(422, last.status.value(), String(last.responseBody ?: ByteArray(0)))
        assertEquals(PROBLEM_TYPES + "invalid-input", json(last).path("type").asText())
        val violations = json(last).path("violations")
        assertEquals(listOf(pointer), violations.map { it.path("pointer").asText() }.distinct())
        assertTrue(violations.all { "not one of the choices" in it.path("message").asText() }, "$violations")
    }

    @Then("the account refuses it by a toll rule at {string}")
    fun refusedByRule(pointer: String) {
        assertEquals(422, last.status.value(), String(last.responseBody ?: ByteArray(0)))
        assertEquals(PROBLEM_TYPES + "rule-refused", json(last).path("type").asText())
        assertEquals(listOf(pointer), json(last).path("violations").map { it.path("pointer").asText() }.distinct())
    }

    private fun openWithTags(id: Long, dollars: Int, tags: List<String>) = runBlocking {
        record(id, AccountEvent.FundsAdded(dollars * Rules.CENTS))
        tags.forEach { record(id, AccountEvent.TagAdded(it)) }
    }

    private fun get(id: Long): EntityExchangeResult<ByteArray> =
        client.get().uri("/accounts/$id").header(HttpHeaders.ACCEPT, HAL_JSON).exchange().expectBody().returnResult()
            .also { state = json(it) }

    private fun forms(): Set<String> = state.path("_forms").fieldNames().asSequence().toSet()

    private suspend fun record(id: Long, event: AccountEvent) {
        events.append(ACCOUNTS, id, events.nextSequenceNo(ACCOUNTS, id), event)
    }

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private companion object {
        const val HAL_JSON = "application/hal+json"
    }
}
