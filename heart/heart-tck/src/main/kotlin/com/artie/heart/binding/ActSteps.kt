package com.artie.heart.binding

import com.artie.heart.binding.HttpWorld.Companion.accept
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/** Acting on a lamp or at the bookmark, and what the answer says. */
class ActSteps(private val http: HttpWorld) {

    @When("^a client posts (.*) to \"(.+)\" as (\\S+) with If-Match \"(.*)\"$")
    fun post(body: String, path: String, dialect: String, ifMatch: String) {
        // A regex step keeps Gherkin's \" escapes; a validator's quotes are part of it.
        val validator = ifMatch.takeIf { it.isNotEmpty() }?.let { http.expand(it.replace("\\\"", "\"")) }
        val headers = mapOf(HttpHeaders.ACCEPT to accept(dialect)) +
            listOfNotNull(validator?.let { HttpHeaders.IF_MATCH to it })
        http.post(path, body, headers)
    }

    /** A bookmark act's owning resource is the service index: the client syncs it, then sends its ETag. */
    @When("a client posts {} to {string} as {word} with the index's ETag")
    fun postWithIndexETag(body: String, path: String, dialect: String) {
        val index = http.get("/", mapOf(HttpHeaders.ACCEPT to accept(dialect)))
        val etag = checkNotNull(index.responseHeaders.eTag) { "the index has no ETag" }
        http.post(path, body, mapOf(HttpHeaders.ACCEPT to accept(dialect), HttpHeaders.IF_MATCH to etag))
    }

    @Then("the response has {word} {string}")
    fun header(name: String, value: String) {
        val isETag = name.equals(HttpHeaders.ETAG, ignoreCase = true)
        val expected = if (isETag) http.expand(value, http.last.responseBody) else value
        assertEquals(expected, http.last.responseHeaders.getFirst(name), name)
    }

    @Then("the response body has {string} {int}")
    fun bodyHas(name: String, value: Int) = assertEquals(value, http.json.path(name).asInt())

    @Then("the response is a problem of type {string}")
    fun problem(type: String) {
        assertEquals("application/problem+json", http.last.responseHeaders.contentType.toString())
        assertEquals("https://w3id.org/heart-rise/problems/$type", http.json.path("type").asText())
    }

    @Then("the response offers")
    fun offers(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("form") }.toSet(),
        http.json.path("_forms").fieldNames().asSequence().toSet(),
    )

    @Then("the response links {string} to an affordance set the server serves")
    fun linksSet(rel: String) {
        val href = http.json.path("_links").path(rel).path("href").asText()
        val results = http.results
        val served = http.get(href)
        http.results = results
        assertEquals(HttpStatus.OK, served.status, href)
    }

    @Then("the response has templates named")
    fun templateNames(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("template") }.toSet(),
        http.json.path("_templates").fieldNames().asSequence().toSet(),
    )

    @Then("the response has templates targeting")
    fun templates(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("target") }.toSet(),
        http.json.path("_templates").map { it.path("target").asText() }.toSet(),
    )

    @Then("the response links its profile {string}")
    fun linksProfile(profile: String) {
        val link = http.last.responseHeaders.getFirst(HttpHeaders.LINK).orEmpty()
        assertTrue("<$profile>; rel=\"profile\"" in link, link)
    }
}
