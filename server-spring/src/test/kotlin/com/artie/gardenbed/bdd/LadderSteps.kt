package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.FORMS_REL
import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.MessageDigest

/** Steps for SPEC §6.4/§6.10.1-6.10.3: which layers arrive where, at each rung. */
class LadderSteps(private val http: HttpWorld) {

    @When("a client gets bed {long} with no Prefer header")
    fun getBedNoPrefer(id: Long) {
        http.get("/beds/$id", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @When("a client gets bed {long} with Prefer {string}")
    fun getBedWithPrefer(id: Long, prefer: String) {
        http.get("/beds/$id", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT, "Prefer" to prefer))
    }

    @When("the client follows the affordance-set link")
    fun followAffordanceSetLink() {
        http.get(affordanceSetHref(lastBedResponse()))
    }

    @When("the client follows the same affordance-set link with Prefer {string}")
    fun followSameLinkWithPrefer(prefer: String) {
        http.get(http.last.path, mapOf("Prefer" to prefer))
    }

    @Then("the response status is {int}")
    fun responseStatus(status: Int) = assertEquals(status, http.last.status, String(http.last.bytes))

    @Then("the response embeds the {string} form with its full schema")
    fun embedsFormWithFullSchema(name: String) = formEmbedsFullSchema(name)

    @Then("the {string} form embeds its full schema")
    fun formEmbedsFullSchema(name: String) {
        val schema = http.last.json.path("_forms").path(name).path("schema")
        assertTrue(schema.has("properties"), "no inline schema body for $name in ${http.last.json}")
    }

    @Then("the {string} form points to its schema by \$id only")
    fun formPointsToSchema(name: String) {
        val schema = http.last.json.path("_forms").path(name).path("schema")
        val fields = schema.fieldNames().asSequence().toList()
        assertEquals(listOf("\$id"), fields, "expected a pointer-only schema for $name in ${http.last.json}")
        assertTrue(schema.path("\$id").asText().startsWith("/schemas/sha256-"))
    }

    @Then("the response has no affordance-set link")
    fun noAffordanceSetLink() =
        assertFalse(http.last.json.path("_links").has(FORMS_REL), "unexpected forms link in ${http.last.json}")

    @Then("the response has no inline forms")
    fun noInlineForms() = assertFalse(http.last.json.has("_forms"), "unexpected _forms in ${http.last.json}")

    @Then("the response has no Preference-Applied header")
    fun noPreferenceApplied() = assertNull(http.last.headers.getFirst("Preference-Applied"))

    @Then("Preference-Applied is {string}")
    fun preferenceApplied(expected: String) = assertEquals(expected, http.last.headers.getFirst("Preference-Applied"))

    @Then("the two affordance-set links differ")
    fun twoLinksDiffer() {
        val (first, second) = http.exchanges.takeLast(2).map(::affordanceSetHref)
        assertNotEquals(first, second, "both rungs linked to $first")
    }

    @Then("the response Cache-Control is {string}")
    fun cacheControl(expected: String) = assertEquals(expected, http.last.headers.cacheControl)

    @Then("the response has no Vary header")
    fun noVary() = assertTrue(http.last.headers.vary.isEmpty(), "Vary: ${http.last.headers.vary}")

    @Then("the response body hashes to the last path segment of its URL")
    fun bodyHashesToUrl() {
        val digest = MessageDigest.getInstance("SHA-256").digest(http.last.bytes)
            .joinToString("") { "%02x".format(it) }
        val segment = http.last.path.substringAfterLast("/sha256-")
        assertTrue(digest.startsWith(segment), "sha256 $digest does not match ${http.last.path}")
    }

    @Then("both affordance-set responses have identical bytes")
    fun identicalBytes() {
        val (first, second) = http.exchanges.takeLast(2)
        assertArrayEquals(first.bytes, second.bytes, "${String(first.bytes)} vs ${String(second.bytes)}")
    }

    private fun lastBedResponse(): Exchange = http.exchanges.last { it.path.startsWith("/beds/") }

    private fun affordanceSetHref(exchange: Exchange): String {
        val href = exchange.json.path("_links").path(FORMS_REL).path("href").asText()
        assertTrue(href.startsWith("/affordance-sets/sha256-"), "no affordance-set link in ${exchange.json}")
        return href
    }
}
