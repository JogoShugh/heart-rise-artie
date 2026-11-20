package com.artie.gardenbed.bdd

import io.cucumber.java.en.When

/** Sending an act to a bed, with the If-Match given. */
class ActSteps(private val http: HttpWorld) {

    /** `none` sends no If-Match at all. */
    @When("a client posts {} to {word} on bed {long} with If-Match {}")
    fun postWithIfMatch(body: String, action: String, id: Long, ifMatch: String) {
        val headers = if (ifMatch == "none") emptyMap() else mapOf("If-Match" to ifMatch)
        http.post("/beds/$id/actions/$action", body, headers)
    }
}
