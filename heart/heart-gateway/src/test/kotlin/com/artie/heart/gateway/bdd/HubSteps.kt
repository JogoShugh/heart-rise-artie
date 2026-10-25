package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.fixtures.Comment
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** github-hub.feature: reads held until what they read changes, woken by GitHub's signed webhooks. */
class HubSteps(private val steps: GitHubSteps) {
    /** How long the last held read took, in milliseconds. */
    private var took = 0L

    /** What happens upstream while the next read is held, in order. */
    private val meanwhile = mutableListOf<() -> Unit>()

    /** The comment made upstream while the next read is held: its issue, repository and id. */
    private var made: Triple<Long, String, Long>? = null

    @Given("the GitHub gateway takes GitHub's webhooks, signed with the secret {string}")
    fun takesWebhooks(secret: String) {
        steps.hubSecret = secret
    }

    @Given("while the next read is held, {string} comments {string} on issue {long} of {string} upstream")
    fun commentsMeanwhile(author: String, body: String, issue: Long, repository: String) {
        meanwhile += {
            val (owner, repo) = repository.split("/")
            val id = (steps.upstream.comments.maxOfOrNull { it.id } ?: 0) + 1
            steps.upstream.comments += Comment(owner, repo, issue, id, author, body)
            made = Triple(issue, repository, id)
        }
    }

    @Given("GitHub sends its webhook for that comment, signed with {string}, {int} milliseconds after the read is sent")
    fun webhookMeanwhile(secret: String, after: Long) {
        val comment = meanwhile.removeLast()
        meanwhile += {
            Thread.sleep(after)
            comment()
            val (issue, repository, id) = checkNotNull(made)
            steps.call(webhook(repository, issue, id, secret))
        }
    }

    @When("{string} gets it again with the ETag she was given, preferring to wait {int} seconds")
    fun waits(caller: String, seconds: Int) {
        val last = steps.lastResponse
        val request = Request(Method.GET, checkNotNull(steps.lastPath))
            .header("Authorization", "Bearer $caller-token").header("Accept", "application/hal+json")
            .header("If-None-Match", checkNotNull(last.header("ETag"))).header("Prefer", "wait=$seconds")
        val upstream = meanwhile.toList().also { meanwhile.clear() }
        val other = Thread.ofVirtual().start { upstream.forEach { it() } }
        val began = System.nanoTime()
        steps.send(request)
        took = (System.nanoTime() - began) / NANOS_PER_MILLI
        other.join()
    }

    @When("GitHub sends its webhook for a comment on issue {long} of {string}, signed with {string}")
    fun sendsWebhook(issue: Long, repository: String, secret: String) =
        steps.send(webhook(repository, issue, COMMENT, secret))

    @Then("the answer came in less than {int} seconds")
    fun cameWithin(seconds: Int) = assertTrue(took < seconds * MILLIS, "took $took ms")

    @Then("the answer came in no less than {int} seconds")
    fun cameAfter(seconds: Int) = assertTrue(took >= seconds * MILLIS, "took $took ms")

    @Then("the answer has an item titled {string}")
    fun hasItem(title: String) {
        val titles = steps.lastAnswer.path("_links").path("item").map { it.path("title").asText() }
        assertTrue(title in titles, "$titles")
    }

    @Then("the answer says Preference-Applied {string}")
    fun applied(preference: String) = assertEquals(preference, steps.lastResponse.header("Preference-Applied"))

    /** GitHub's webhook for a comment, [id], made on [issue], its body signed with [secret]. */
    private fun webhook(repository: String, issue: Long, id: Long, secret: String): Request {
        val api = "https://api.github.com/repos/$repository"
        val body = """{"action":"created",""" +
            """"issue":{"url":"$api/issues/$issue","number":$issue},""" +
            """"comment":{"url":"$api/issues/comments/$id","id":$id},""" +
            """"repository":{"url":"$api","full_name":"$repository"}}"""
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val signature = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
        return Request(Method.POST, "/hooks/github").header("X-GitHub-Event", "issue_comment")
            .header("X-Hub-Signature-256", "sha256=$signature").header("Content-Type", "application/json").body(body)
    }

    private companion object {
        const val MILLIS = 1000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val COMMENT = 777L
    }
}
