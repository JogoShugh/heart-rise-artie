package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.http4k.Credentials
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Path
import java.security.MessageDigest

/** The gateway in front of a small GitHub, known by a slice of GitHub's own description. */
class GitHubSteps {
    private val github = GitHubUpstream()
    private val mapper = ObjectMapper()
    private var connector = ""
    private var forwarding = false
    private var started: Result<HttpHandler>? = null
    private val responses = mutableListOf<Response>()

    private val gateway: HttpHandler get() = (started ?: start().also { started = it }).getOrThrow()
    private val json: JsonNode get() = mapper.readTree(responses.last().bodyString())

    @Given("the GitHub gateway reads GitHub's description with the connector {string}")
    fun reads(name: String) {
        connector = name
        started = null
    }

    @Given("the GitHub gateway reads GitHub's description with the shipped connector")
    fun shipped() = reads("github")

    @Given("GitHub's own example pull request, number {long} of {string}, is {}")
    fun example(number: Long, repository: String, state: String) {
        val (owner, repo) = repository.split("/")
        val pull = Pull(owner, repo, number, "open", "Amazing new feature", head = EXAMPLE_HEAD)
        when (state) {
            "open and clean" -> Unit
            "a draft" -> pull.apply { draft = true; mergeableState = "draft" }
            "behind its base" -> pull.mergeableState = "behind"
            "blocked" -> pull.mergeableState = "blocked"
            "still being checked" -> pull.apply { mergeable = null; mergeableState = "unknown" }
            "in conflict" -> pull.apply { mergeable = false; mergeableState = "dirty" }
            "merged" -> pull.apply { this.state = "closed"; merged = true }
            "closed" -> pull.state = "closed"
            else -> error("no state $state")
        }
        github.pulls += pull
    }

    @Given("the GitHub gateway calls GitHub as each caller")
    fun forwarding() {
        forwarding = true
        started = null
    }

    @Given("GitHub knows the callers {string} and {string}")
    fun callers(first: String, second: String) {
        listOf(first, second).forEach { github.callers["$it-token"] = it }
    }

    @Given("{string} can't push to {string}")
    fun cantPush(caller: String, repository: String) {
        github.readOnlyFor += "$caller $repository"
    }

    @When("{string} gets {string} through the GitHub gateway")
    fun getAs(caller: String, path: String) {
        responses += gateway(
            Request(Method.GET, path).header("Authorization", "Bearer $caller-token")
                .header("Accept", "application/hal+json"),
        )
    }

    @When("a client with no credential gets {string} through the GitHub gateway")
    fun getWithout(path: String) {
        responses += gateway(Request(Method.GET, path).header("Accept", "application/hal+json"))
    }

    @Then("GitHub was never asked for {string}")
    fun neverAsked(path: String) = assertTrue(github.requests.none { it.uri.path == path }, "${github.requests}")

    @Then("GitHub answered the last request for {string} with {int}")
    fun answeredWith(path: String, status: Int) {
        val last = github.requests.indexOfLast { it.uri.path == path }
        assertEquals(status, github.statuses[last])
    }

    @Then("GitHub's last request for {string} carried no {string}")
    fun carriedNo(path: String, header: String) =
        assertNull(github.requests.last { it.uri.path == path }.header(header))

    @Given("pull request {long} of {string} is {string} upstream, titled {string}")
    fun pull(number: Long, repository: String, state: String, title: String) {
        val (owner, repo) = repository.split("/")
        github.pulls += Pull(owner, repo, number, state, title)
    }

    @When("the GitHub gateway starts")
    fun starts() {
        started = start()
    }

    @When("a client gets {string} through the GitHub gateway")
    fun get(path: String) {
        responses += gateway(
            Request(Method.GET, path).header("Authorization", "Bearer $TOKEN").header("Accept", "application/hal+json"),
        )
    }

    @Then("the GitHub gateway answers {int}")
    fun answers(code: Int) = assertEquals(code, responses.last().status.code, responses.last().bodyString())

    @Then("the profile describes exactly {string}")
    fun profile(ids: String) {
        val descriptors = json.path("alps").path("descriptor").map { it.path("id").asText() }
        assertEquals(ids.split(", ").toSet(), descriptors.toSet())
    }

    @Then("the index form {string} targets {string}")
    fun targets(key: String, href: String) =
        assertEquals(href, json.path("_forms").path(key).path("_links").path("target").path("href").asText(), "$json")

    @Then("the index form {string} field {string} has the type {string}")
    fun fieldType(key: String, field: String, type: String) =
        assertEquals(type, property(key, field).path("type").asText())

    @Then("the index form {string} field {string} is one of {string}")
    fun fieldEnum(key: String, field: String, values: String) =
        assertEquals(values.split(", "), property(key, field).path("enum").map { it.asText() })

    @Then("the index form {string} field {string} is described as {string}")
    fun fieldDescription(key: String, field: String, description: String) =
        assertEquals(description, property(key, field).path("description").asText())

    @Then("the answer links {string} to {string}")
    fun links(rel: String, href: String) = assertEquals(href, json.path("_links").path(rel).path("href").asText())

    @Then("GitHub was asked for {string} with {string} {string}")
    fun askedWith(path: String, header: String, value: String) {
        val asked = checkNotNull(github.requests.lastOrNull { it.uri.toString() == path }) { "never asked for $path" }
        assertEquals(value, asked.header(header))
    }

    @Then("GitHub was last asked for {string}")
    fun asked(path: String) = assertEquals(path, checkNotNull(github.last).uri.toString())

    @Then("the answer lists {string} titled {string}")
    fun lists(href: String, title: String) {
        val items = json.path("_links").path("item").map { it.path("href").asText() to it.path("title").asText() }
        assertEquals(listOf(href to title), items)
    }

    @Given("pull request {long}'s head is at {string}")
    fun head(number: Long, sha: String) {
        github.pulls.first { it.number == number }.head = sha
    }

    @Given("pull request {long}'s head moves to {string} just before GitHub merges it")
    fun moves(number: Long, sha: String) {
        github.movesBeforeMerge[number] = sha
    }

    @Given("pull request {long} can't be merged, GitHub says {string}")
    fun unmergeable(number: Long, message: String) {
        github.unmergeable[number] = message
    }

    @Given("pull request {long} is a draft")
    fun draft(number: Long) {
        pull(number).apply { draft = true; mergeableState = "draft" }
    }

    @Given("pull request {long} was merged")
    fun merged(number: Long) {
        pull(number).apply { state = "closed"; merged = true }
    }

    @Given("GitHub hasn't yet worked out whether pull request {long} can be merged")
    fun unknown(number: Long) {
        pull(number).apply { mergeable = null; mergeableState = "unknown" }
    }

    @Given("GitHub has worked out that pull request {long} can be merged cleanly")
    fun known(number: Long) {
        pull(number).apply { mergeable = true; mergeableState = "clean" }
    }

    @Given("GitHub says pull request {long} is {string}")
    fun mergeableState(number: Long, state: String) {
        pull(number).mergeableState = state
    }

    @Given("the caller can't push to {string}")
    fun readOnly(repository: String) {
        github.readOnly += repository
    }

    @Then("the GitHub gateway offers nothing")
    fun offersNothing() =
        assertEquals(emptySet<String>(), json.path("_forms").fieldNames().asSequence().toSet(), "$json")

    @Then("the GitHub gateway says {string} is absent because {string}")
    fun absent(key: String, reason: String) {
        val href = json.path("_links").path("hr:unavailable").path("href").asText()
        val explanation = mapper.readTree(
            gateway(Request(Method.GET, href).header("Authorization", "Bearer $TOKEN")).bodyString(),
        )
        val reasons = explanation.path("unavailable")
            .associate { it.path("rel").asText() to it.path("reason").asText() }
        assertEquals(reason, reasons["/profiles/github#${key.substringAfter(':')}"], "$reasons")
    }

    @When("the client sends {string} with {} and the ETag it holds")
    fun send(key: String, body: String) {
        val etag = checkNotNull(responses.last().header("ETag"))
        val target = json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        responses += gateway(
            Request(Method.POST, target).header("Authorization", "Bearer $TOKEN")
                .header("Accept", "application/hal+json").header("If-Match", etag)
                .header("Content-Type", "application/json").body(body),
        )
    }

    @Then("the GitHub gateway offers {string}")
    fun offers(keys: String) = assertEquals(
        keys.split(", ").filter { it.isNotEmpty() }.toSet(),
        json.path("_forms").fieldNames().asSequence().toSet(),
        "$json",
    )

    @Then("GitHub was asked to {word} {string} with {}")
    fun askedTo(method: String, path: String, body: String) {
        val sent = github.requests.lastOrNull { it.method.name == method && it.uri.path == path }
        assertEquals(mapper.readTree(body), mapper.readTree(checkNotNull(sent) { "no $method $path" }.bodyString()))
    }

    @Then("the form {string} has the fields {string}")
    fun formFields(key: String, fields: String) {
        val properties = json.path("_forms").path(key).path("schema").path("properties")
        // Schemas are canonical JSON (RFC 8785), so their members are in code-point order, not the file's.
        assertEquals(fields.split(", ").toSet(), properties.fieldNames().asSequence().toSet())
    }

    @Then("the form {string} field {string} is one of {string}")
    fun formFieldEnum(key: String, field: String, values: String) {
        val property = json.path("_forms").path(key).path("schema").path("properties").path(field)
        assertEquals(values.split(", "), property.path("enum").map { it.asText() })
    }

    @Then("the problem's detail is {string}")
    fun detail(detail: String) = assertEquals(detail, json.path("detail").asText(), "$json")

    @Then("the GitHub gateway refuses to start, saying {string}")
    fun refuses(says: String) = assertEquals(says, started?.exceptionOrNull()?.message)

    private fun pull(number: Long): Pull = github.pulls.first { it.number == number }

    private fun property(key: String, field: String): JsonNode =
        json.path("_forms").path(key).path("schema").path("properties").path(field)

    private fun start(): Result<HttpHandler> = runCatching {
        // A connector the tests make up is a test resource; one that ships is the gateway's own.
        val folder = javaClass.getResource("/connectors/$connector")?.let { Path.of(it.toURI()) }
            ?: Path.of("connectors", connector).also { check(it.toFile().isDirectory) { "no connector $connector" } }
        val loaded = Connector.load(folder, mapper)
        val description = checkNotNull(javaClass.getResource("/github/openapi.json")).readText()
        val auth = if (forwarding) UpstreamAuth.Forwarded else UpstreamAuth.None
        val credentials =
            if (forwarding) Credentials.forwarded() else Credentials.parse(users = "", tokens = sha256(TOKEN))
        Gateway(GatewaySpec(description, loaded.include), github.handler, loaded.profile, auth, connector = loaded)
            .handler(credentials)
    }

    private companion object {
        const val TOKEN = "github-gateway-token"

        /** The head sha of GitHub's own example pull request. */
        const val EXAMPLE_HEAD = "6dcb09b5b57875f334f61aebed695e2e4193db5e"

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
