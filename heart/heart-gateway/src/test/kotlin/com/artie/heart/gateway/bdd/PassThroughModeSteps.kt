package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/** github-pass-through.feature: a request the caller asks the gateway to pass through to GitHub, untouched. */
class PassThroughModeSteps(private val github: GitHubSteps) {
    private val mapper = ObjectMapper()
    private val answer: JsonNode get() = mapper.readTree(github.lastResponse.bodyString())

    @When("{string} gets {string} through the GitHub gateway, passed through")
    fun getPassed(caller: String, path: String) = github.send(passed(Request(Method.GET, path), "Bearer $caller-token"))

    @When("{string} gets {string} through the GitHub gateway, as HEART")
    fun getHeart(caller: String, path: String) = github.getAs(caller, path)

    @When("{string} sends {word} {string} with {} through the GitHub gateway, passed through")
    fun sendPassed(caller: String, method: String, path: String, body: String) = github.send(
        passed(Request(Method.valueOf(method), path), "Bearer $caller-token")
            .header("Content-Type", "application/json").body(body),
    )

    @When("a caller with no credential gets {string} through the GitHub gateway, passed through")
    fun getPassedAnonymous(path: String) = github.send(passed(Request(Method.GET, path), null))

    @Then("the answer is GitHub's own, with its ETag and no HEART controls")
    fun githubsOwnWithETag() {
        assertNotNull(github.lastResponse.header("ETag"), "no ETag")
        githubsOwn()
    }

    @Then("the answer is GitHub's own, with no HEART controls")
    fun githubsOwn() {
        assertFalse(answer.has("_links") || answer.has("_forms"), "$answer")
        assertEquals("application/json", github.lastResponse.header("Content-Type")?.substringBefore(';'))
    }

    @Then("the answer is HEART's, with its forms")
    fun heartsOwn() = assertTrue(answer.has("_forms"), "$answer")

    @Then("the answer's message is {string}")
    fun message(text: String) = assertEquals(text, answer.path("message").asText(), "$answer")

    @Then("GitHub was asked nothing")
    fun askedNothing() = assertTrue(github.upstream.requests.isEmpty(), "${github.upstream.requests}")

    /** [request] as a plain GitHub client sends it, asking the gateway to pass it through. */
    private fun passed(request: Request, authorization: String?): Request {
        val plain = request.header("Accept", "application/vnd.github+json").header(MODE, "pass-through")
        return authorization?.let { plain.header("Authorization", it) } ?: plain
    }

    private companion object {
        const val MODE = "X-Gateway-Mode"
    }
}
