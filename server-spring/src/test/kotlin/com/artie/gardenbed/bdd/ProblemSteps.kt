package com.artie.gardenbed.bdd

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for SPEC §6.2's error bodies: RFC 9457 problem details. */
class ProblemSteps(private val http: HttpWorld) {

    @Then("the response is a problem of type {string} with status {int}")
    fun isProblem(type: String, status: Int) {
        val body = String(http.last.bytes)
        assertEquals("application/problem+json", http.last.headers.contentType?.toString(), body)
        assertEquals(PROBLEMS + type, http.last.json.path("type").asText(), body)
        assertEquals(status, http.last.json.path("status").asInt(), body)
        assertTrue(http.last.json.path("title").asText().isNotBlank(), body)
    }

    @Then("the problem's {string} member is {string}")
    fun member(name: String, value: String) = assertEquals(value, http.last.json.path(name).asText())

    @Then("the problem lists a violation at {string}")
    fun violationAt(pointer: String) {
        val violations = http.last.json.path("violations")
        assertTrue(
            violations.any { it.path("pointer").asText() == pointer && it.path("message").asText().isNotBlank() },
            "no violation at $pointer in $violations",
        )
    }

    private companion object {
        const val PROBLEMS = "https://github.com/jogoshugh/heart-rise/problems/"
    }
}
