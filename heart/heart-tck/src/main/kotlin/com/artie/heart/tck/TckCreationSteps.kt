package com.artie.heart.tck

import com.artie.heart.Dialect
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/**
 * The `@creation` scenarios: the type's creation, offered by the service
 * index as a POST form on the collection and conditional on the index's ETag.
 */
class TckCreationSteps(private val fixture: TckFixture, private val world: TckWorld) {
    private val headers = mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType)
    private var countBefore = 0

    private val creation: TckAct
        get() = checkNotNull(fixture.creation) { "the fixture names no creation; filter out @creation" }

    @Then("the index offers the fixture's creation as a POST form on the collection")
    fun offered() {
        val form = checkNotNull(world.forms()[creation.key]) { "the index has no form ${creation.key}" }
        assertEquals("POST", form.path("method").asText())
        assertEquals("/${fixture.collection}", form.path("_links").path("target").path("href").asText())
    }

    @When("^the client sends the creation with (its valid body|its invalid body) and ($VALIDATORS)$")
    fun send(body: String, validator: String) {
        countBefore = count()
        val index = world.get("/", headers).responseHeaders.eTag
        val ifMatch = when (validator) {
            "no If-Match" -> emptyMap()
            "an If-Match that is stale" -> mapOf(HttpHeaders.IF_MATCH to "\"stale\"")
            else -> mapOf(HttpHeaders.IF_MATCH to checkNotNull(index) { "the index has no ETag" })
        }
        val text = if (body == "its valid body") creation.body else checkNotNull(creation.invalidBody)
        world.post("/${fixture.collection}", text, headers + ifMatch)
    }

    @Then("the response names the new resource in Location and in Content-Location")
    fun located() {
        val location = world.last.responseHeaders.location?.toString().orEmpty()
        assertTrue(location.matches(Regex("/${fixture.collection}/\\d+")), "Location: $location")
        assertEquals(location, world.last.responseHeaders.getFirst(HttpHeaders.CONTENT_LOCATION))
        assertEquals(location, world.json.path("_links").path("self").path("href").asText())
    }

    @Then("the new resource can be read where Location says")
    fun readable() {
        val location = checkNotNull(world.last.responseHeaders.location).toString()
        val created = world.last.responseHeaders.eTag
        val read = world.get(location, headers)
        assertEquals(HttpStatus.OK, read.status)
        assertEquals(created, read.responseHeaders.eTag)
    }

    @Then("the collection holds as many resources as before")
    fun nothingCreated() = assertEquals(countBefore, count())

    /** How many resources a search with no term finds. */
    private fun count(): Int =
        world.get("/${fixture.collection}", headers).let { world.json.path("count").asInt() }

    private companion object {
        const val VALIDATORS = "no If-Match|an If-Match that is stale|the index's ETag"
    }
}
