package com.artie.heart.tck

import com.artie.heart.PROBLEM_TYPES
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** Acting on the fixture's resource: success, and each refusal in SPEC §6.2's order. */
class TckActSteps(private val fixture: TckFixture, private val world: TckWorld) {

    @When("^the client sends (the offered act|the absent act) with (.+) and ($VALIDATORS)$")
    fun send(act: String, body: String, validator: String) {
        val chosen = if (act == OFFERED) fixture.offered else fixture.absent
        val text = if (body == "its invalid body") checkNotNull(chosen.invalidBody) else chosen.body
        val ifMatch = when (validator) {
            "no If-Match" -> emptyMap()
            "an If-Match that is stale" -> mapOf(HttpHeaders.IF_MATCH to "\"stale\"")
            else -> mapOf(HttpHeaders.IF_MATCH to checkNotNull(world.heldETag))
        }
        world.post("${world.resource}/actions/${chosen.path}", text, world.heldHeaders + ifMatch, chosen.method)
    }

    @Then("the explanation gives the absent act's reason")
    fun reason() {
        val reasons = world.json.path("unavailable").map { it.path("reason").asText() }
        assertTrue(checkNotNull(fixture.absent.reason) in reasons, "reasons: $reasons")
    }

    @Then("the response names the resource in Content-Location")
    fun contentLocation() =
        assertEquals(world.resource, world.last.responseHeaders.getFirst(HttpHeaders.CONTENT_LOCATION))

    @Then("the response has a new ETag")
    fun newETag() = assertNotEquals(world.heldETag, world.last.responseHeaders.eTag)

    @Then("the response is a problem of type {string}")
    fun problem(type: String) = assertEquals(PROBLEM_TYPES + type, world.json.path("type").asText())

    @Then("the resource is unchanged")
    fun unchanged() =
        assertEquals(world.heldETag, world.get(world.resource, world.heldHeaders).responseHeaders.eTag)

    private companion object {
        const val OFFERED = "the offered act"
        const val VALIDATORS = "no If-Match|an If-Match that is stale|the ETag it holds"
    }
}
