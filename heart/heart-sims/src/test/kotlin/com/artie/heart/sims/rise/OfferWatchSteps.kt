package com.artie.heart.sims.rise

import com.artie.heart.Signatures
import com.artie.heart.sims.github.GitHubService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** offer-watch.feature: the watcher, the GitHub gateway in memory, and the caller's replies. */
class OfferWatchSteps {
    private val mapper = ObjectMapper()
    private val github = GitHubService().apply { reset() }
    private val replies = ReplyService(
        origin = REPLIES, realm = "ARTIE's replies", tokens = listOf(REPLY_TOKEN),
        result = """{"type": "object", "additionalProperties": true}""",
        ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        requestKeys = { mapOf(WatcherKey.KEY_ID to WatcherKey.pair.public) },
    )
    private val toReplies: HttpHandler = { replies.handler(it.uri(Uri.of(it.uri.path).query(it.uri.query))) }
    private val toGitHub: HttpHandler = { request ->
        github.handler(request.uri(Uri.of(request.uri.path)).header("Authorization", "Bearer ${GitHubService.TOKEN}"))
    }
    private val watcher = OfferWatcher(
        reader = toGitHub,
        answerer = Answerer(toReplies, WatcherKey.signer, "Bearer $REPLY_TOKEN"),
        callerKeys = { mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public) },
    )
    private var answer: Response? = null

    @Given("the GitHub gateway's pull request {long}, whose mergeability GitHub has not worked out yet")
    fun unknown(number: Long) = github.mergeable(number, null)

    @Given("the watching caller's reply {word} is awaiting")
    fun awaiting(reply: String) = replies.open(reply)

    @When(
        "^the caller asks the watcher to be told when pull request (\\d+) offers \"([^\"]+)\", with reply (\\S+), " +
            "(signed|unsigned|waiting at most \\d+ looks?)$",
    )
    fun asks(number: Long, relation: String, reply: String, how: String) {
        val looks = Regex("\\d+").find(how)?.value
        val etag = checkNotNull(watcher.handler(get("/")).header("ETag"))
        val body = mapper.writeValueAsString(
            mapOf(
                "resource" to "$GITHUB/repos/acme/web/pulls/$number",
                "offers" to relation,
                "_links" to mapOf(
                    "hr:reply-to" to mapOf("href" to "$REPLIES/replies/$reply"),
                    "hr:keys" to mapOf("href" to "$REPLIES${Signatures.KEYS}"),
                ),
            ) + listOfNotNull(looks?.let { "looks" to it.toInt() }),
        )
        var request = get(OfferWatcher.WATCH).method(Method.POST).header("Content-Type", "application/json")
            .header("If-Match", etag)
        if (how != "unsigned") {
            val target = OfferWatcher.ORIGIN + OfferWatcher.WATCH
            val s = ArtieKey.signer.sign("POST", target, body, etag, Instant.now().epochSecond)
            request = request.header("Content-Digest", s.contentDigest).header("Signature-Input", "sig1=${s.params}")
                .header("Signature", "sig1=:${s.signature}:")
        }
        answer = watcher.handler(request.body(body))
        answer?.header("Location")?.let { replies.asked(reply, OfferWatcher.ORIGIN + it) }
    }

    @Then("the watcher answers {int}, the watch at {string}")
    fun created(status: Int, location: String) {
        assertEquals(status, answer?.status?.code, answer?.bodyString())
        assertEquals(location, answer?.header("Location"))
    }

    @Then("the watcher answers {int}")
    fun answers(status: Int) = assertEquals(status, answer?.status?.code, answer?.bodyString())

    @When("the watcher looks, and merge is not offered yet")
    fun looksNotYet() = watcher.look()

    @When("GitHub works out that pull request {long} can be merged, and the watcher looks again")
    fun worksOut(number: Long) {
        github.mergeable(number, true)
        watcher.look()
    }

    @Then("reply {word} is awaiting, told the watcher is still looking")
    fun stillLooking(reply: String) {
        val now = state(reply)
        assertEquals("awaiting", now.path("state").asText(), "$now")
        assertTrue(now.path("note").asText().startsWith("Still looking"), "$now")
    }

    @Then("reply {word} is completed, its result linking pull request {long} as {string} offers it")
    fun completed(reply: String, number: Long, relation: String) {
        val now = state(reply)
        assertEquals("completed", now.path("state").asText(), "$now")
        val about = now.path("result").path("_links").path("about").path("href").asText()
        assertEquals("$GITHUB/repos/acme/web/pulls/$number", about)
        assertEquals(relation, now.path("result").path("offers").asText())
    }

    @Then("reply {word} is failed, its problem linking pull request {long}")
    fun failed(reply: String, number: Long) {
        val now = state(reply)
        assertEquals("failed", now.path("state").asText(), "$now")
        assertTrue("$GITHUB/repos/acme/web/pulls/$number" in now.toString(), "$now")
    }

    @Then("watch {long} is {string}")
    fun standing(id: Long, standing: String) = assertEquals(standing, watcher.watch(id)?.standing)

    private fun state(reply: String): JsonNode = mapper.valueToTree(checkNotNull(replies.state(reply)))

    private fun get(path: String) = Request(Method.GET, path).header("Authorization", "Bearer ${OfferWatcher.TOKEN}")
        .header("Accept", "application/hal+json")

    private companion object {
        const val REPLIES = "http://localhost:8160"
        const val GITHUB = "http://localhost:8098"
        const val REPLY_TOKEN = "watcher-reply-token"
    }
}
