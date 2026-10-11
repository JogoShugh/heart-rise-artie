package com.artie.heart.spring.bdd

import com.artie.heart.PastEvent
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration
import java.time.Instant

class ResourceSteps(private val client: WebTestClient, private val store: LampStore, private val http: HttpWorld) {
    private val json: JsonNode get() = ObjectMapper().readTree(http.last.responseBody)

    @Before
    fun emptyStore() = store.lamps.clear()

    @Given("lamp {long} is {string} at version {int} in the lamp server")
    fun lampIs(id: Long, status: String, version: Int) {
        store.lamps[id] = Lamp(id, status, brightness = 50, version = version)
    }

    @Given("lamp {long} was switched on {int} days ago and dimmed to {int} {int} day ago, in the lamp server")
    fun lampWithPast(id: Long, onAgo: Long, brightness: Int, dimAgo: Long) {
        val events = listOf(
            PastEvent(1, "SwitchedOn", Instant.now().minus(Duration.ofDays(onAgo)), emptyMap<String, Any>()),
            PastEvent(2, "Dimmed", Instant.now().minus(Duration.ofDays(dimAgo)), mapOf("brightness" to brightness)),
        )
        store.events[id] = events
        store.lamps[id] = store.at(id, Instant.now())
    }

    @When("a client gets lamp {long} as {word} as of {int} days ago")
    fun getAsOf(id: Long, dialect: String, days: Long) =
        getAs("/lamps/$id?asOf=${Instant.now().minus(Duration.ofDays(days))}", dialect)

    @Then("the history is {string}, numbered {int} to {int}")
    fun history(types: String, from: Int, to: Int) {
        assertEquals(types, json.joinToString(", ") { it.path("type").asText() })
        assertEquals((from..to).toList(), json.map { it.path("sequenceNo").asInt() })
    }

    @Then("the last event's {string} is {int}")
    fun lastEvent(name: String, value: Int) = assertEquals(value, json.last().path("event").path(name).asInt())

    @When("a client gets {string} again with the ETag it was given")
    fun again(path: String) {
        val etag = checkNotNull(http.last.responseHeaders.eTag)
        val accept = checkNotNull(http.last.requestHeaders.getFirst(HttpHeaders.ACCEPT))
        get(path, mapOf(HttpHeaders.ACCEPT to accept, HttpHeaders.IF_NONE_MATCH to etag))
    }

    @Then("the response lists the items {}")
    fun items(items: String) {
        val listed = json.path("_links").path("item").joinToString(", ") {
            "\"${it.path("href").asText()}\" titled \"${it.path("title").asText()}\""
        }
        assertEquals(items, listed)
    }

    @When("a client gets {string} as {word}")
    fun getAs(path: String, dialect: String) = get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect)))

    @When("a client gets {string} as {word} with Prefer {string}")
    fun getWithPrefer(path: String, dialect: String, prefer: String) =
        get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect), "Prefer" to prefer))

    @When("a client gets {string} as {word} with If-None-Match {string}")
    fun getIfNoneMatch(path: String, dialect: String, etag: String) =
        get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect), HttpHeaders.IF_NONE_MATCH to etag))

    @When("a client gets {string} with Accept {string}")
    fun getWithAccept(path: String, value: String) = get(path, mapOf(HttpHeaders.ACCEPT to value))

    @When("^a client posts (.*) to \"(.+)\" as (\\S+) with If-Match \"(.*)\"$")
    fun post(body: String, path: String, dialect: String, ifMatch: String) {
        val spec = client.post().uri(path)
            .header(HttpHeaders.ACCEPT, accept(dialect))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
        // A regex step keeps Gherkin's \" escapes; a validator's quotes are part of it.
        if (ifMatch.isNotEmpty()) spec.header(HttpHeaders.IF_MATCH, ifMatch.replace("\\\"", "\""))
        http.results = http.results + spec.bodyValue(body).exchange().expectBody().returnResult()
    }

    @Then("the response has {word} {string}")
    fun header(name: String, value: String) = assertEquals(value, http.last.responseHeaders.getFirst(name), name)

    @Then("the response links its profile {string}")
    fun linksProfile(profile: String) {
        val link = http.last.responseHeaders.getFirst(HttpHeaders.LINK).orEmpty()
        assertTrue("<$profile>; rel=\"profile\"" in link, link)
    }

    @Then("the response offers {string}")
    fun offers(keys: String) = assertEquals(names(keys), json.path("_forms").fieldNames().asSequence().toSet())

    @Then("the response links {string} to an affordance set the server serves")
    fun linksSet(rel: String) {
        val href = json.path("_links").path(rel).path("href").asText()
        val served = client.get().uri(href).exchange().expectBody().returnResult()
        assertEquals(200, served.status.value(), href)
    }

    @Then("the response has templates targeting {string}")
    fun templates(targets: String) =
        assertEquals(names(targets), json.path("_templates").map { it.path("target").asText() }.toSet())

    @Then("the response body has {string} {int}")
    fun bodyHas(name: String, value: Int) = assertEquals(value, json.path(name).asInt())

    @Then("the response is a problem of type {string}")
    fun problem(type: String) {
        assertEquals("application/problem+json", http.last.responseHeaders.contentType.toString())
        assertEquals("https://github.com/jogoshugh/heart-rise/problems/$type", json.path("type").asText())
    }

    @Then("lamp {long} is still at version {int}")
    fun stillAt(id: Long, version: Int) = assertEquals(version, store.lamps.getValue(id).version)

    private fun get(path: String, headers: Map<String, String>) {
        val spec = client.get().uri(path)
        headers.forEach { (k, v) -> spec.header(k, v) }
        http.results = http.results + spec.exchange().expectBody().returnResult()
    }

    private fun accept(dialect: String) = when (dialect) {
        "hal-schema-forms" -> "application/hal+json"
        "hal-forms" -> "application/prs.hal-forms+json"
        else -> error("unknown dialect $dialect")
    }

    private fun names(list: String): Set<String> = list.split(",").map { it.trim() }.toSet()
}
