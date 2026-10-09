package com.artie.gardenbed.bdd

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals

/** Steps for SPEC §6.2/§6.10.5: conditional acts and their error codes. */
class ActSteps(private val http: HttpWorld, private val beds: InMemoryBeds, private val store: InMemoryEventStore) {

    @When("a client waters bed {long} with {int} ml and no If-Match")
    fun waterWithoutIfMatch(id: Long, milliliters: Int) {
        http.post("/beds/$id/actions/water-cell", """{"milliliters": $milliliters}""")
    }

    @When("the client posts {} to {word} on bed {long} using the ETag it holds")
    fun postWithHeldETag(body: String, action: String, id: Long) {
        val layer1 = http.lastLayer1()
        val etag = checkNotNull(layer1.headers.eTag) { "client holds no ETag" }
        http.post("/beds/$id/actions/$action", body, http.negotiationHeaders(layer1) + ("If-Match" to etag))
    }

    /** `none` sends no If-Match at all. */
    @When("a client posts {} to {word} on bed {long} with If-Match {}")
    fun postWithIfMatch(body: String, action: String, id: Long, ifMatch: String) {
        val headers = if (ifMatch == "none") emptyMap() else mapOf("If-Match" to ifMatch)
        http.post("/beds/$id/actions/$action", body, headers)
    }

    @Then("bed {long} is still at state version {int}")
    fun stateVersion(id: Long, version: Int) = runBlocking {
        assertEquals(version, store.currentState(id, beds.label(id)).lastSequenceNo)
    }
}
