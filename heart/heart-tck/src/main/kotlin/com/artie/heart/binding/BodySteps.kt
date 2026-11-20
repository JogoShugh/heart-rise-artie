package com.artie.heart.binding

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** What a response's body and headers hold, and what a problem says. */
class BodySteps(private val http: HttpWorld) {

    @Then("the response has no body")
    fun noBody() = assertTrue((http.last.responseBody ?: ByteArray(0)).isEmpty(), "a body came back")

    @Then("the response body is exactly {}")
    fun bodyIsExactly(expected: String) = assertEquals(ObjectMapper().readTree(expected), http.json)

    @Then("the response carries no {word} header")
    fun carriesNo(name: String) {
        val value = http.last.responseHeaders.getFirst(name)
        assertNull(value, "$name: $value")
    }

    @Then("the response has no Vary header")
    fun noVary() = assertTrue(http.last.responseHeaders.vary.isEmpty(), "${http.last.responseHeaders.vary}")

    @Then("the last two responses have identical bytes")
    fun identical() {
        val (first, second) = http.results.takeLast(2)
        assertArrayEquals(first.responseBody, second.responseBody)
    }

    @Then("the problem's {string} member is {string}")
    fun member(name: String, value: String) = assertEquals(value, http.json.path(name).asText(), "${http.json}")

    @Then("the problem lists a violation at {string}")
    fun violation(pointer: String) =
        assertTrue(http.json.path("violations").any { it.path("pointer").asText() == pointer }, "${http.json}")

    @Then("the problem's detail is {string}")
    fun detail(detail: String) = assertEquals(detail, http.json.path("detail").asText(), "${http.json}")
}
