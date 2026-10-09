package com.artie.gardenbed.bdd

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for SPEC §6.1/§6.2/§6.7: Layer 1 cache headers, ETags, and conditional requests. */
class RevalidationSteps(private val http: HttpWorld) {

    @Given("a client got bed {long} as {word} with Prefer {string}")
    @When("a client gets bed {long} as {word} with Prefer {string}")
    fun getBedAs(id: Long, dialect: String, prefer: String) {
        val headers = mutableMapOf("Accept" to HttpWorld.acceptFor(dialect))
        if (prefer.isNotBlank()) headers["Prefer"] = prefer
        http.get("/beds/$id", headers)
    }

    @When("the client revalidates bed {long} with the ETag it holds")
    fun revalidate(id: Long) {
        val held = http.lastLayer1()
        http.get("/beds/$id", held.requestHeaders + ("If-None-Match" to heldETag()))
    }

    @When("the client revalidates bed {long} at Prefer {string} with the ETag it holds")
    fun revalidateAtOtherRung(id: Long, prefer: String) {
        val held = http.lastLayer1()
        http.get("/beds/$id", held.requestHeaders + mapOf("Prefer" to prefer, "If-None-Match" to heldETag()))
    }

    @When("the client waters bed {long} with {int} ml using the ETag it holds")
    fun waterWithHeldETag(id: Long, milliliters: Int) =
        water(id, milliliters, http.negotiationHeaders(http.lastLayer1()))

    @When("the client waters bed {long} with {int} ml using the ETag and headers it synced with")
    fun waterAsSynced(id: Long, milliliters: Int) = waterWithHeldETag(id, milliliters)

    @When("the client waters bed {long} with {int} ml using the ETag it holds but Prefer {string}")
    fun waterWithOtherPrefer(id: Long, milliliters: Int, prefer: String) {
        val synced = http.negotiationHeaders(http.lastLayer1()) - "Prefer"
        water(id, milliliters, if (prefer.isBlank()) synced else synced + ("Prefer" to prefer))
    }

    private fun water(id: Long, milliliters: Int, negotiation: Map<String, String>) {
        val headers = negotiation + ("If-Match" to heldETag())
        http.post("/beds/$id/actions/water-cell", """{"milliliters": $milliliters}""", headers)
    }

    @Then("the response ETag is {string}")
    fun responseETag(expected: String) = assertEquals("\"$expected\"", http.last.headers.eTag)

    @Then("the response ETag is the one the client holds")
    fun responseETagIsHeld() {
        val held = http.exchanges.dropLast(1).last { it.path.startsWith("/beds/") }.headers.eTag
        assertEquals(held, http.last.headers.eTag)
    }

    @Then("the response has no body")
    fun noBody() = assertTrue(http.last.bytes.isEmpty(), "unexpected body: ${String(http.last.bytes)}")

    private fun heldETag(): String = checkNotNull(http.lastLayer1().headers.eTag) { "client holds no ETag" }
}
