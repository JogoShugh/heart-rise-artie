package com.artie.heart.gateway.bdd

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** github-dam-*.feature: the controls the gateway's owner puts in front of GitHub for a client passing through. */
class DamSteps(private val github: GitHubSteps) {

    /** The ETag of the last read passed through, as a plain client keeps it. */
    private var read: String? = null

    @Given("the GitHub gateway's dam is {string}")
    fun dam(words: String) {
        github.dam = words
    }

    @Then("the answer's ETag is the gateway's own")
    fun gatewaysOwnETag() {
        val etag = github.lastResponse.header("ETag").orEmpty()
        assertTrue(etag.startsWith("\"gw-"), "ETag $etag")
    }

    @When("{string} gets {string} through the GitHub gateway, passed through, with the ETag it read")
    fun getAgain(caller: String, path: String) =
        github.send(passed(Request(Method.GET, path), caller).header("If-None-Match", checkNotNull(lastRead())))

    @When("nothing happens upstream")
    fun nothing() {
        lastRead()
    }

    @When("a teammate retitles issue {long} of {string} upstream to {string}")
    fun teammateRetitles(number: Long, repository: String, title: String) {
        lastRead()
        val (owner, repo) = repository.split("/")
        github.upstream.issues.first { it.owner == owner && it.repo == repo && it.number == number }.title = title
    }

    @When("{string} sends {word} {string} with {} through the GitHub gateway, passed through, with the ETag it read")
    fun sendWithETag(caller: String, method: String, path: String, body: String) =
        github.send(sent(caller, method, path, body).header("If-Match", checkNotNull(lastRead())))

    @When("{string} sends {word} {string} with {} through the GitHub gateway, passed through, with no validator")
    fun sendWithout(caller: String, method: String, path: String, body: String) =
        github.send(sent(caller, method, path, body))

    @Then("GitHub was not asked to {word} {string}")
    fun wasNotAsked(method: String, path: String) = assertTrue(
        github.upstream.requests.none { it.method.name == method && it.uri.path == path },
        "${github.upstream.requests}",
    )

    @Then("the answer's message mentions {string}")
    fun messageMentions(words: String) {
        val message = github.lastAnswer.path("message").asText()
        assertTrue(words in message, message)
    }

    @Then("the answer's Server-Timing names {string} and {string}, each with a duration")
    fun serverTiming(first: String, second: String) {
        val timing = github.lastResponse.header("Server-Timing").orEmpty()
        listOf(first, second).forEach { name ->
            assertTrue(Regex("""(^|,\s*)$name;dur=\d+(\.\d+)?""").containsMatchIn(timing), "Server-Timing: $timing")
        }
    }

    @Then("the issue's title upstream is {string}")
    fun titleUpstream(title: String) = assertEquals(title, github.upstream.issues.single().title)

    /** The ETag of the read passed through just before, kept as a plain client keeps it. */
    private fun lastRead(): String? {
        github.lastResponse.header("ETag")?.let { read = it }
        return read
    }

    private fun sent(caller: String, method: String, path: String, body: String) =
        passed(Request(Method.valueOf(method), path), caller).header("Content-Type", "application/json").body(body)

    private fun passed(request: Request, caller: String) = request.header("Accept", "application/vnd.github+json")
        .header("X-Gateway-Mode", "pass-through").header("Authorization", "Bearer $caller-token")
}
