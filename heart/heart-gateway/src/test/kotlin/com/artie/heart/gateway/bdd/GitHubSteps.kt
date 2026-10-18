package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.gateway.fixtures.Comment
import com.artie.heart.gateway.fixtures.GitHubUpstream
import com.artie.heart.gateway.fixtures.Issue
import com.artie.heart.gateway.fixtures.Pull
import com.artie.heart.http4k.Credentials
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Path
import java.security.MessageDigest
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** The gateway in front of a small GitHub, known by a slice of GitHub's own description. */
class GitHubSteps {
    private val github = GitHubUpstream()
    private val mapper = ObjectMapper()
    private var connector = ""
    private var forwarding = false
    private var started: Result<HttpHandler>? = null
    private val responses = mutableListOf<Response>()

    /** The credential of the last read, which the client then acts with. */
    private var authorization = "Bearer $TOKEN"

    private val gateway: HttpHandler get() = (started ?: start().also { started = it }).getOrThrow()
    private val json: JsonNode get() = mapper.readTree(responses.last().bodyString())

    /** The gateway's last answer, for the step classes beside this one. */
    internal val lastAnswer: JsonNode get() = json

    /** The small GitHub behind the gateway, for the step classes beside this one. */
    internal val upstream: GitHubUpstream get() = github

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
            "a draft" -> pull.draft = true
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

    @Given("GitHub knows these callers")
    fun callers(table: DataTable) {
        table.asMaps().map { it.getValue("caller") }.forEach { github.callers["$it-token"] = it }
    }

    @Given("{string} can't push to {string}")
    fun cantPush(caller: String, repository: String) {
        github.readOnlyFor += "$caller $repository"
    }

    @When("{string} gets {string} through the GitHub gateway")
    fun getAs(caller: String, path: String) {
        authorization = "Bearer $caller-token"
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

    @Then("the index form {string} targets {string}")
    fun targets(key: String, href: String) =
        assertEquals(href, json.path("_forms").path(key).path("_links").path("target").path("href").asText(), "$json")

    @Then("the index form {string} field {string} has the type {string}")
    fun fieldType(key: String, field: String, type: String) =
        assertEquals(type, property(key, field).path("type").asText())

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
        // GitHub says a draft's mergeable_state is clean; only draft: true says it's a draft.
        pull(number).draft = true
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

    @Then("the GitHub gateway offers")
    fun offers(table: DataTable) =
        assertEquals(table.column("act"), json.path("_forms").fieldNames().asSequence().toSet(), "$json")

    @Then("the profile describes these relations, among others")
    fun profile(table: DataTable) {
        val descriptors = json.path("alps").path("descriptor").map { it.path("id").asText() }.toSet()
        assertEquals(emptySet<String>(), table.column("relation") - descriptors, "not described")
    }

    @Then("the profile describes none of these relations")
    fun profileLacks(table: DataTable) {
        val descriptors = json.path("alps").path("descriptor").map { it.path("id").asText() }.toSet()
        assertEquals(emptySet<String>(), table.column("relation").intersect(descriptors), "described")
    }

    @Then("the index form {string} field {string} is one of")
    fun indexFieldEnum(key: String, field: String, table: DataTable) {
        val values = property(key, field).path("enum").map { it.asText() }
        assertEquals(table.asMaps().map { it.getValue("value") }, values)
    }

    @Then("the form {string} has the fields")
    fun formFields(key: String, table: DataTable) {
        val properties = json.path("_forms").path(key).path("schema").path("properties")
        // Schemas are canonical JSON (RFC 8785), so their members are in code-point order, not the file's.
        assertEquals(table.column("field"), properties.fieldNames().asSequence().toSet())
    }

    @Then("the form {string} field {string} is one of")
    fun formFieldEnum(key: String, field: String, table: DataTable) {
        val property = json.path("_forms").path(key).path("schema").path("properties").path(field)
        assertEquals(table.asMaps().map { it.getValue("value") }, property.path("enum").map { it.asText() })
    }

    @Then("the GitHub gateway says these acts are absent, and why")
    fun absentWhy(table: DataTable) {
        val reasons = reasons()
        table.asMaps().forEach { row ->
            val act = row.getValue("act").substringAfter(':')
            assertEquals(row["reason"], reasons["/profiles/github#$act"], "$reasons")
        }
    }

    @Then("GitHub's own example pull request, number {long} of {string}, offers in each state")
    fun offersInEachState(number: Long, repository: String, table: DataTable) {
        val acts = table.asLists().first().drop(1)
        table.asMaps().forEach { row ->
            val forms = readExample(number, repository, row.getValue("state"))
            val expected = acts.filter { row[it] == "yes" }.map { "github:$it" }.toSet()
            assertEquals(expected, forms, "offers when ${row["state"]}")
        }
    }

    @Then("GitHub's own example pull request, number {long} of {string}, says why an act is absent")
    fun absentInEachState(number: Long, repository: String, table: DataTable) {
        table.asMaps().forEach { row ->
            readExample(number, repository, row.getValue("state"))
            val why = reasons()["/profiles/github#${row["act"]}"]
            assertEquals(row["reason"], why, "why no ${row["act"]} when ${row["state"]}")
        }
    }

    /** The example pull request in [state], read through the gateway as ada; the forms it offers. */
    private fun readExample(number: Long, repository: String, state: String): Set<String> {
        github.pulls.clear()
        example(number, repository, state)
        getAs("ada", "/repos/$repository/pulls/$number")
        return json.path("_forms").fieldNames().asSequence().toSet()
    }

    /** Why each act the last read leaves out is absent, by its relation. */
    private fun reasons(): Map<String, String> {
        val href = json.path("_links").path("hr:unavailable").path("href").asText()
        val explanation = mapper.readTree(
            gateway(Request(Method.GET, href).header("Authorization", authorization)).bodyString(),
        )
        return explanation.path("unavailable").associate { it.path("rel").asText() to it.path("reason").asText() }
    }

    private fun DataTable.column(name: String): Set<String> = asMaps().map { it.getValue(name) }.toSet()

    @Given("{string} has these issues upstream")
    fun issues(repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        table.asMaps().forEach { row ->
            github.issues += Issue(
                owner, repo, row.getValue("number").toLong(), row.getValue("title"), row.getValue("state"),
                row["state_reason"]?.ifEmpty { null }, row.getValue("locked").toBoolean(), row.getValue("author"),
            )
        }
    }

    @Given("the repository {string} has issues turned off upstream")
    fun issuesOff(repository: String) {
        github.issuesOff += repository
    }

    @Given("the repository {string} is archived upstream")
    fun archived(repository: String) {
        github.archived += repository
    }

    @Then("the answer's Location is {string}")
    fun location(path: String) = assertEquals(path, responses.last().header("Location"), responses.last().bodyString())

    @Given("issue {long} of {string} has these comments upstream")
    fun comments(number: Long, repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        table.asMaps().forEach { row ->
            github.comments += Comment(
                owner, repo, number, row.getValue("id").toLong(), row.getValue("author"), row.getValue("body"),
            )
        }
    }

    @Given("issue {long} of {string} is a sub-issue of issue {long} upstream")
    fun subIssue(number: Long, repository: String, parent: Long) {
        github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == parent }.subIssues += number
    }

    @Then("comment {long} of {string} offers each caller")
    fun commentOffersEachCaller(id: Long, repository: String, table: DataTable) {
        table.asMaps().forEach { row ->
            github.access[row.getValue("caller")] = row.getValue("access")
            getAs(row.getValue("caller"), "/repos/$repository/issues/comments/$id")
            val offered = "github:edit" in json.path("_forms").fieldNames().asSequence().toSet()
            assertEquals(row["edit"] == "yes", offered, "edit for ${row["caller"]} with ${row["access"]}")
            if (!offered) assertEquals(row["reason"], reasons()["/profiles/github#edit"])
        }
    }

    @Given("issue {long} of {string} is locked upstream")
    fun issueLocked(number: Long, repository: String) {
        github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == number }.locked = true
    }

    @Given("issue {long} of {string} has these labels and assignees upstream")
    fun labelled(number: Long, repository: String, table: DataTable) {
        github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == number }.apply {
            table.asMaps().forEach { row ->
                row["label"]?.takeIf { it.isNotEmpty() }?.let { labels += it }
                row["assignee"]?.takeIf { it.isNotEmpty() }?.let { assignees += it }
            }
        }
    }

    @Then("the forms ask for these fields")
    fun formsAsk(table: DataTable) {
        table.asMaps().groupBy { it.getValue("act") }.forEach { (act, rows) ->
            val properties = json.path("_forms").path(act).path("schema").path("properties")
            assertEquals(rows.map { it.getValue("field") }.toSet(), properties.fieldNames().asSequence().toSet(), act)
        }
    }

    @Then("the issue's labels are now")
    fun labelsNow(table: DataTable) =
        assertEquals(table.asMaps().map { it.getValue("label") }, json.path("labels").map { it.path("name").asText() })

    @Then("issue {long} of {string} offers each caller")
    fun offersEachCaller(number: Long, repository: String, table: DataTable) {
        val acts = table.asLists().first().drop(2)
        table.asMaps().forEach { row ->
            github.access[row.getValue("caller")] = row.getValue("access")
            getAs(row.getValue("caller"), "/repos/$repository/issues/$number")
            val forms = json.path("_forms").fieldNames().asSequence().toSet()
            acts.forEach { act ->
                val offered = "github:$act" in forms
                assertEquals(row[act] == "yes", offered, "$act for ${row["caller"]} with ${row["access"]}: $forms")
            }
        }
    }

    @Then("issue {long} of {string} says why, to each caller")
    fun whyEachCaller(number: Long, repository: String, table: DataTable) {
        table.asMaps().forEach { row ->
            github.access[row.getValue("caller")] = row.getValue("access")
            getAs(row.getValue("caller"), "/repos/$repository/issues/$number")
            val why = reasons()["/profiles/github#${row["act"]}"]
            assertEquals(row["reason"], why, "why no ${row["act"]} for ${row["caller"]} with ${row["access"]}")
        }
    }

    @Then("the answer lists")
    fun answerLists(table: DataTable) {
        val items = json.path("_links").path("item")
            .map { mapOf("href" to it.path("href").asText(), "title" to it.path("title").asText()) }
        assertEquals(table.asMaps(), items, "$json")
    }

    @Then("each issue state offers")
    fun issueStatesOffer(table: DataTable) {
        val acts = table.asLists().first().drop(1)
        table.asMaps().forEach { row ->
            val forms = readIssue(row.getValue("state"))
            val expected = acts.filter { row[it] == "yes" }.map { "github:$it" }.toSet()
            assertEquals(expected, forms, "offers when ${row["state"]}")
        }
    }

    @Then("each issue state says why an act is absent")
    fun issueStatesWhy(table: DataTable) {
        table.asMaps().forEach { row ->
            readIssue(row.getValue("state"))
            val why = reasons()["/profiles/github#${row["act"]}"]
            assertEquals(row["reason"], why, "why no ${row["act"]} when ${row["state"]}")
        }
    }

    @Then("GitHub was asked to {word} {string}")
    fun askedToWithout(method: String, path: String) =
        assertTrue(github.requests.any { it.method.name == method && it.uri.path == path }, "${github.requests}")

    /** Issue 20 of acme/rockets in [state], read through the gateway as ada; the forms it offers. */
    private fun readIssue(state: String): Set<String> {
        github.issues.removeIf { it.number == ISSUE_UNDER_TEST }
        val (open, reason) = when {
            state.startsWith("open") -> "open" to null
            state.contains("not planned") -> "closed" to "not_planned"
            else -> "closed" to "completed"
        }
        val locked = state.contains("locked")
        github.issues += Issue("acme", "rockets", ISSUE_UNDER_TEST, "An issue", open, reason, locked, "ada")
        getAs("ada", "/repos/acme/rockets/issues/$ISSUE_UNDER_TEST")
        return json.path("_forms").fieldNames().asSequence().toSet()
    }

    @Then("the GitHub gateway offers nothing")
    fun offersNothing() =
        assertEquals(emptySet<String>(), json.path("_forms").fieldNames().asSequence().toSet(), "$json")

    @Then("the GitHub gateway says {string} is absent because {string}")
    fun absent(key: String, reason: String) =
        assertEquals(reason, reasons()["/profiles/github#${key.substringAfter(':')}"], "${reasons()}")

    @When("the client sends {string} with {} and the ETag it holds")
    fun send(key: String, body: String) {
        val etag = checkNotNull(responses.last().header("ETag"))
        val target = json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        responses += gateway(
            Request(Method.POST, target).header("Authorization", authorization)
                .header("Accept", "application/hal+json").header("If-Match", etag)
                .header("Content-Type", "application/json").body(body),
        )
    }

    @Given("pull request {long}'s branch is behind its base")
    fun behind(number: Long) {
        pull(number).behind = true
    }

    @Given("pull request {long} is closed upstream")
    fun closedUpstream(number: Long) {
        pull(number).state = "closed"
    }

    @Then("the pull request's title is now {string}")
    fun titleNow(title: String) = assertEquals(title, json.path("title").asText(), "$json")

    @Then("the pull request now asks {string} for a review")
    fun asksForReview(login: String) =
        assertTrue(json.path("requested_reviewers").any { it.path("login").asText() == login }, "$json")

    @Then("GitHub was never asked to {word} {string}")
    fun neverAskedTo(method: String, path: String) =
        assertTrue(github.requests.none { it.method.name == method && it.uri.path == path }, "${github.requests}")

    @Then("the problem names the violation {string}")
    fun violation(message: String) =
        assertTrue(json.path("violations").any { it.path("message").asText() == message }, "$json")

    @Then("GitHub was asked to {word} {string} with {}")
    fun askedTo(method: String, path: String, body: String) {
        val sent = github.requests.lastOrNull { it.method.name == method && it.uri.path == path }
        assertEquals(mapper.readTree(body), mapper.readTree(checkNotNull(sent) { "no $method $path" }.bodyString()))
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
        val spec = GatewaySpec(description, loaded.reads)
        Gateway(spec, github.handler, loaded.profile, auth, connector = loaded, upstreamUrl = "https://api.github.com")
            .handler(credentials)
    }

    private companion object {
        const val TOKEN = "github-gateway-token"
        const val ISSUE_UNDER_TEST = 20L

        /** The head sha of GitHub's own example pull request. */
        const val EXAMPLE_HEAD = "6dcb09b5b57875f334f61aebed695e2e4193db5e"

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
