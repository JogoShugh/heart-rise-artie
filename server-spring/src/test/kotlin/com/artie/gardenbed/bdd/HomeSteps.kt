package com.artie.gardenbed.bdd

import com.artie.gardenbed.web.Rung
import com.artie.gardenbed.web.ServingLimits
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for SPEC §6.11.1: the JSON Home index, and servers capped below L2. */
class HomeSteps(private val http: HttpWorld, private val limits: ServingLimits) {

    @Given("the server serves rungs up to {word}")
    fun servesUpTo(rung: String) {
        limits.maxRung = Rung.valueOf(rung)
    }

    /** The cap is a singleton in the Spring context; every scenario starts at the default. */
    @After
    fun restoreLimit() {
        limits.maxRung = Rung.L2
    }

    @When("a client gets the home document")
    fun getHome() {
        http.get("/", mapOf("Accept" to "application/json-home"))
    }

    @Then("the home document names {string} at template {string}")
    fun namesResource(rel: String, template: String) =
        assertEquals(template, resource(rel).path("hrefTemplate").asText(), "resource $rel in ${http.last.json}")

    @Then("the {string} hint formats offers {string} with profile {string}")
    fun formatOffered(rel: String, mediaType: String, profile: String) {
        val format = hints(rel).path("formats").path(mediaType)
        assertTrue(format.isObject, "no format $mediaType in ${hints(rel)}")
        assertEquals(profile, format.path("links").path("profile").asText())
    }

    @Then("the {string} hint {string} is {string}")
    fun hintIs(rel: String, hint: String, expected: String) =
        assertEquals(expected, hints(rel).path(hint).joinToString(", ") { it.asText() })

    @Then("the {string} hint {string} is absent")
    fun hintAbsent(rel: String, hint: String) =
        assertTrue(hints(rel).path(hint).isMissingNode, "$hint in ${hints(rel)}")

    @Then("Preference-Applied is absent")
    fun preferenceAppliedAbsent() = assertNull(http.last.headers.getFirst("Preference-Applied"))

    private fun resource(rel: String): JsonNode = http.last.json.path("resources").path(rel)

    private fun hints(rel: String): JsonNode = resource(rel).path("hints")
}
