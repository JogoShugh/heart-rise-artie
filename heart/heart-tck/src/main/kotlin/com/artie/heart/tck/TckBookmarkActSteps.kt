package com.artie.heart.tck

import com.artie.heart.Dialect
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/**
 * The `@bookmark-act` scenarios: an act the service index offers, a form on
 * the index's own `/actions/{path}`, conditional on the index's ETag, whose
 * result is a resource that did not exist before.
 */
class TckBookmarkActSteps(private val fixture: TckFixture, private val world: TckWorld) {
    private val headers = mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType)
    private var countBefore = 0

    private val act: TckAct
        get() = checkNotNull(fixture.bookmarkAct) { "the fixture names no bookmark act; filter out @bookmark-act" }

    @Then("the index offers the fixture's bookmark act as a form on its own actions")
    fun offered() {
        val form = checkNotNull(world.forms()[act.key]) { "the index has no form ${act.key}" }
        assertEquals("POST", form.path("method").asText())
        assertEquals("/actions/${act.path}", form.path("_links").path("target").path("href").asText())
    }

    @When("^the client sends the bookmark act with (its valid body|its invalid body) and ($VALIDATORS)$")
    fun send(body: String, validator: String) {
        countBefore = count()
        val index = world.get("/", headers).responseHeaders.eTag
        val ifMatch = when (validator) {
            "no If-Match" -> emptyMap()
            "an If-Match that is stale" -> mapOf(HttpHeaders.IF_MATCH to "\"stale\"")
            else -> mapOf(HttpHeaders.IF_MATCH to checkNotNull(index) { "the index has no ETag" })
        }
        val text = if (body == "its valid body") act.body else checkNotNull(act.invalidBody)
        world.post("/actions/${act.path}", text, headers + ifMatch)
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
        val made = world.last.responseHeaders.eTag
        val read = world.get(location, headers)
        assertEquals(HttpStatus.OK, read.status)
        assertEquals(made, read.responseHeaders.eTag)
    }

    @Then("the collection holds as many resources as before")
    fun nothingAdded() = assertEquals(countBefore, count())

    /** How many resources a search with no term finds. */
    private fun count(): Int =
        world.get("/${fixture.collection}", headers).let { world.json.path("count").asInt() }

    private companion object {
        const val VALIDATORS = "no If-Match|an If-Match that is stale|the index's ETag"
    }
}
