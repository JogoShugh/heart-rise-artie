package com.artie.heart.tck

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** Reading the fixture's resource: the layers, the headers, revalidation and integrity. */
class TckReadSteps(private val fixture: TckFixture, private val world: TckWorld) {

    @Given("the fixture's resource is ready")
    fun ready() {
        world.id = runBlocking { fixture.ready() }
    }

    @When("a client gets the resource as hal-schema-forms")
    fun getResource() = world.hold(mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType))

    @When("a client gets the resource as hal-schema-forms with Prefer {string}")
    fun getResourceWithPrefer(prefer: String) =
        world.hold(mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType, "Prefer" to prefer))

    @When("the client gets it again with the ETag it was given")
    fun again() {
        world.get(world.resource, world.heldHeaders + (HttpHeaders.IF_NONE_MATCH to checkNotNull(world.heldETag)))
    }

    @When("the client follows the {string} link")
    fun follow(rel: String) {
        val link = world.json.path("_links").path(rel)
        world.linkIntegrity = link.path("integrity").asText(null)
        world.get(link.path("href").asText())
    }

    @Then("the response offers the offered act, with its full schema and a concrete target")
    fun offersOffered() {
        val form = world.forms().getValue(fixture.offered.key)
        assertTrue(form.path("schema").has("properties"), "schema not inline: $form")
        val target = form.path("_links").path("target").path("href").asText()
        assertEquals("${world.resource}/actions/${fixture.offered.path}", target)
    }

    @Then("the response does not offer the absent act")
    fun doesNotOfferAbsent() = assertFalse(fixture.absent.key in world.forms())

    @Then("the response has no inline forms")
    fun noInlineForms() = assertFalse(world.json.has("_forms"))

    @Then("the body matches the integrity of the link that led to it")
    fun matchesIntegrity() {
        val body = String(checkNotNull(world.last.responseBody), Charsets.UTF_8)
        assertEquals(world.linkIntegrity, ContentAddress.integrity(body))
    }

    @Then("the offered act's target is a template, filled by the fixture's pointers")
    fun template() {
        val form = world.forms().getValue(fixture.offered.key)
        assertEquals(true, form.path("_links").path("target").path("templated").asBoolean())
        val pointers = form.path("hr:templatePointers").properties().associate { it.key to it.value.asText() }
        assertEquals(fixture.pointers, pointers)
    }

}
