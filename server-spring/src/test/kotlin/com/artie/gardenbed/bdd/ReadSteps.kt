package com.artie.gardenbed.bdd

import io.cucumber.java.en.Given

/** Reading a bed in a dialect, at a rung. */
class ReadSteps(private val http: HttpWorld) {

    @Given("a client got bed {long} as {word} with Prefer {string}")
    fun getBedAs(id: Long, dialect: String, prefer: String) {
        val headers = mutableMapOf("Accept" to HttpWorld.acceptFor(dialect))
        if (prefer.isNotBlank()) headers["Prefer"] = prefer
        http.get("/beds/$id", headers)
    }
}
