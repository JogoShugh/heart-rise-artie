package com.artie.heart.binding

import com.artie.heart.binding.HttpWorld.Companion.accept
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.time.Instant

/** Reading resources, each in the representation a scenario asks for, and a lamp's past. */
class ReadSteps(private val http: HttpWorld) {

    @When("a client gets {string} as {word}")
    fun getAs(path: String, dialect: String) {
        http.get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect)))
    }

    @When("a client gets {string} as {word} with Prefer {string}")
    fun getWithPrefer(path: String, dialect: String, prefer: String) {
        http.get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect), "Prefer" to prefer))
    }

    @When("a client gets {string} as {word} with If-None-Match {string}")
    fun getIfNoneMatch(path: String, dialect: String, etag: String) {
        http.get(path, mapOf(HttpHeaders.ACCEPT to accept(dialect), HttpHeaders.IF_NONE_MATCH to http.expand(etag)))
    }

    @When("a client gets {string} with Accept {string}")
    fun getWithAccept(path: String, value: String) {
        http.get(path, mapOf(HttpHeaders.ACCEPT to value))
    }

    @When("a client gets {string} with Accept {string} and Accept-Profile {string}")
    fun getNegotiated(path: String, accept: String, profile: String) {
        val headers = mapOf(HttpHeaders.ACCEPT to accept, "Accept-Profile" to profile)
        http.get(path, headers.filterValues { it.isNotBlank() })
    }

    @When("a client gets {string} again with the ETag it was given")
    fun again(path: String) {
        val etag = checkNotNull(http.last.responseHeaders.eTag)
        val accept = checkNotNull(http.last.requestHeaders.getFirst(HttpHeaders.ACCEPT))
        http.get(path, mapOf(HttpHeaders.ACCEPT to accept, HttpHeaders.IF_NONE_MATCH to etag))
    }

    @When("a client gets lamp {long} as {word} as of {int} days ago")
    fun getAsOf(id: Long, dialect: String, days: Long) =
        getAs("/lamps/$id?asOf=${Instant.now().minus(Duration.ofDays(days))}", dialect)

    @Then("the history is")
    fun history(table: DataTable) {
        val history = http.json
            .map { mapOf("number" to it.path("sequenceNo").asText(), "type" to it.path("type").asText()) }
        assertEquals(table.asMaps(), history)
    }

    @Then("the last event's {string} is {int}")
    fun lastEvent(name: String, value: Int) = assertEquals(value, http.json.last().path("event").path(name).asInt())
}
