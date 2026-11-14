package com.artie.heart.sims.ci

import com.artie.heart.Signatures
import com.artie.heart.sims.rise.Answerer
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.ReplyService
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
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

/** A Jenkins in memory: what it was asked to build, and what each build archived and logged. */
class FakeJenkins : Jenkins {
    val asked = mutableListOf<Pair<String, Map<String, String>>>()
    val archived = mapOf("smoke.heart.yaml" to "heart: 1\nkind: kept\nsteps: []\n")
    val console = "== checkout 9b1e2d4\nFinished: SUCCESS\n"

    override fun build(job: String, parameters: Map<String, String>) {
        asked += job to parameters
    }

    override fun artifact(job: String, number: Int, path: String): ByteArray? = archived[path]?.toByteArray()

    override fun log(job: String, number: Int): String = console
}

/** ci-wrapper.feature: the wrapper, Jenkins in memory, and the caller's replies, each at its own origin. */
class CiSteps {
    private val mapper = ObjectMapper()
    private val jenkins = FakeJenkins()
    private val replies = ReplyService(
        origin = REPLIES, realm = "ARTIE's replies", tokens = listOf(REPLY_TOKEN), result = CiWrapper.RESULT,
        ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        requestKeys = { mapOf(CiKey.KEY_ID to CiKey.pair.public) },
    )
    private val client: HttpHandler = { request ->
        check(request.uri.authority == Uri.of(REPLIES).authority) { "no party at ${request.uri}" }
        replies.handler(request.uri(Uri.of(request.uri.path).query(request.uri.query)))
    }
    private val wrapper = CiWrapper(
        jenkins = jenkins,
        answerer = Answerer(client, CiKey.signer, "Bearer $REPLY_TOKEN"),
        hookSecret = SECRET,
        callerKeys = { mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public) },
    )
    private var answer: Response? = null

    @Given("a Jenkins job app, behind the wrapper")
    fun jenkins() = Unit

    @Given("the caller's reply {word} is awaiting")
    fun awaiting(reply: String) = replies.open(reply)

    @When("^the caller asks the wrapper to build commit \"(\\w+)\", with reply (\\S+), (signed|unsigned)$")
    fun asks(commit: String, reply: String, signed: String) {
        answer = ask(commit, reply, signed == "signed")
    }

    @Given("the caller has asked to build commit {string}, with reply {word}")
    fun asked(commit: String, reply: String) {
        val made = ask(commit, reply, signed = true)
        assertEquals(201, made.status.code, made.bodyString())
        replies.asked(reply, CiWrapper.ORIGIN + made.header("Location"))
    }

    private fun ask(commit: String, reply: String, signed: Boolean): Response {
        val etag = checkNotNull(wrapper.handler(get("/")).header("ETag"))
        val body = mapper.writeValueAsString(
            mapOf(
                "commit" to commit,
                "_links" to mapOf(
                    "hr:reply-to" to mapOf("href" to "$REPLIES/replies/$reply"),
                    "hr:keys" to mapOf("href" to "$REPLIES${Signatures.KEYS}"),
                ),
            ),
        )
        var request = get(CiWrapper.BUILD).method(Method.POST).header("Content-Type", "application/json")
            .header("If-Match", etag)
        if (signed) {
            val target = CiWrapper.ORIGIN + CiWrapper.BUILD
            val s = ArtieKey.signer.sign("POST", target, body, etag, Instant.now().epochSecond)
            request = request.header("Content-Digest", s.contentDigest).header("Signature-Input", "sig1=${s.params}")
                .header("Signature", "sig1=:${s.signature}:")
        }
        return wrapper.handler(request.body(body))
    }

    @Then("the wrapper answers {int}, the build at {string}")
    fun created(status: Int, location: String) {
        assertEquals(status, answer?.status?.code, answer?.bodyString())
        assertEquals(location, answer?.header("Location"))
    }

    @Then("the wrapper answers {int}")
    fun answers(status: Int) = assertEquals(status, answer?.status?.code, answer?.bodyString())

    @Then("Jenkins was asked to build job app with these parameters")
    fun parameters(table: DataTable) =
        assertEquals(
            listOf("app" to table.asMaps().associate { it.getValue("name") to it.getValue("value") }),
            jenkins.asked,
        )

    @Then("Jenkins was asked to build nothing")
    fun nothing() = assertTrue(jenkins.asked.isEmpty(), "${jenkins.asked}")

    @Then("build {long} is {string}, and links these")
    fun buildLinks(id: Long, standing: String, table: DataTable) {
        standing(id, standing)
        links(id, table)
    }

    @Then("build {long} is {string}")
    fun standing(id: Long, standing: String) = assertEquals(standing, read(id).path("standing").asText())

    @Then("build {long} links these")
    fun links(id: Long, table: DataTable) {
        val links = read(id).path("_links")
        table.asMaps().forEach {
            assertEquals(it.getValue("href"), links.path(it.getValue("relation")).path("href").asText(), "$links")
        }
    }

    @When("^Jenkins notifies the wrapper that build (\\d+) is (\\w+) ?(\\w*), with (the secret|no secret)$")
    fun notifies(id: Int, phase: String, status: String, secret: String) {
        val build = linkedMapOf<String, Any>(
            "number" to id + JENKINS_OFFSET, "phase" to phase, "url" to "job/app/${id + JENKINS_OFFSET}/",
            "parameters" to mapOf("SHA" to "9b1e2d4", "RELEASE_BUILD" to id.toString(), "PREVIEW_PORT" to "8180"),
        )
        if (status.isNotEmpty()) build["status"] = status
        val query = if (secret == "the secret") "?secret=$SECRET" else ""
        answer = wrapper.handler(
            Request(Method.POST, CiWrapper.HOOK + query).header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(mapOf("name" to "app", "url" to "job/app/", "build" to build))),
        )
    }

    @Then("reply {word} is now {word}, by an answer signed by the wrapper, naming the build it answers")
    fun told(reply: String, state: String) {
        val now = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(checkNotNull(replies.state(reply)))
        assertEquals(state, now.path("state").asText(), "$now")
        val inReplyTo = now.path("_links").path("hr:in-reply-to").path("href").asText()
        assertEquals("${CiWrapper.ORIGIN}/builds/1", inReplyTo, "$now")
    }

    @Then("reply {word}'s result links the same three")
    fun resultLinks(reply: String) {
        val now = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(checkNotNull(replies.state(reply)))
        val links = now.path("result").path("_links")
        assertEquals("http://localhost:8180/", links.path(CiWrapper.DEPLOYMENT).path("href").asText(), "$now")
        val smoke = links.path(CiWrapper.SMOKE_TEST).path("href").asText()
        assertEquals("${CiWrapper.ORIGIN}/builds/1/artifacts/${CiWrapper.SMOKE_SCRIPT}", smoke)
        assertEquals("${CiWrapper.ORIGIN}/builds/1/log", links.path(CiWrapper.LOG).path("href").asText())
    }

    @Then("reply {word} is now awaiting, unanswered")
    fun unanswered(reply: String) {
        val now = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(checkNotNull(replies.state(reply)))
        assertEquals("awaiting", now.path("state").asText())
        assertTrue(now.path("_links").path("hr:in-reply-to").isMissingNode, "$now")
    }

    @When("the caller reads {string}")
    fun reads(path: String) {
        answer = wrapper.handler(get(path))
    }

    @Then("the wrapper answers {int}, with what Jenkins archived as {string}")
    fun archived(status: Int, name: String) {
        answers(status)
        assertEquals(jenkins.archived.getValue(name), answer?.bodyString())
    }

    @Then("the wrapper answers {int}, with Jenkins' console text")
    fun console(status: Int) {
        answers(status)
        assertEquals(jenkins.console, answer?.bodyString())
    }

    private fun read(id: Long) =
        mapper.readTree(wrapper.handler(get("/builds/$id")).bodyString())

    private fun get(path: String) = Request(Method.GET, path).header("Authorization", "Bearer ${CiWrapper.TOKEN}")
        .header("Accept", "application/hal+json")

    private companion object {
        const val REPLIES = "http://localhost:8160"
        const val REPLY_TOKEN = "ci-reply-token"
        const val SECRET = "hook-test"
        const val JENKINS_OFFSET = 90
    }
}
