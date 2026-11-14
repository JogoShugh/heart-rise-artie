package com.artie.heart.sims.github

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** github-git.feature: the small GitHub, behind the gateway, keeping acme/web's pull requests in a real repository. */
class GitHubGitSteps {
    private val mapper = ObjectMapper()
    private lateinit var repository: GitRepository
    private lateinit var github: GitHubService
    private lateinit var mainBefore: String
    private var answer: Response? = null
    private var read: JsonNode = mapper.createObjectNode()

    @Given("a git repository whose main has a README, and whose branch {string} adds the line {string}")
    fun repository(branch: String, line: String) {
        val path = Files.createTempDirectory("library-app")
        fun git(vararg args: String) {
            val process = ProcessBuilder(listOf("git", "-C", path.toString()) + args).redirectErrorStream(true).start()
            val done = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
            check(done) { process.inputStream.bufferedReader().readText() }
        }
        git("init", "-q", "-b", "main")
        git("config", "user.name", "Ada Lovelace")
        git("config", "user.email", "ada@example.invalid")
        Files.writeString(path.resolve("README.md"), "# Riverside Public Library\n")
        git("add", "-A")
        git("commit", "-q", "-m", "The library")
        git("checkout", "-q", "-b", branch)
        Files.writeString(path.resolve("README.md"), "# Riverside Public Library\n\n$line\n")
        git("commit", "-q", "-am", "Say the renewal rule")
        git("checkout", "-q", "main")
        repository = GitRepository(path)
        mainBefore = repository.head("main")
    }

    @Given("the small GitHub keeps acme\\/web's pull requests in that repository")
    fun keeps() {
        github = GitHubService(repository = repository).apply { reset() }
    }

    @When("the gateway reads pull request {int} of acme\\/web")
    fun reads(number: Int) {
        read = mapper.readTree(github.handler(get("/repos/acme/web/pulls/$number")).bodyString())
    }

    @Then("its head sha is the commit at the tip of {string}")
    fun head(branch: String) = assertEquals(repository.head(branch), read.path("head").path("sha").asText(), "$read")

    @When("pull request {int} of acme\\/web is merged by squashing, through the gateway")
    fun merges(number: Int) {
        val pull = "/repos/acme/web/pulls/$number"
        val etag = checkNotNull(github.handler(get(pull, HAL_FORMS)).header("ETag"))
        answer = github.handler(
            get("$pull/actions/merge", HAL_FORMS).method(Method.PUT).header("If-Match", etag)
                .header("Content-Type", "application/json").body("""{"merge_method": "squash"}"""),
        )
    }

    @Then("the merge is done")
    fun done() = assertTrue(answer?.status?.successful == true, "${answer?.status} ${answer?.bodyString()}")

    @Then("main's latest commit is a new one, whose README has the line {string}")
    fun mainHas(line: String) {
        assertNotEquals(mainBefore, repository.head("main"))
        assertTrue(line in repository.show("main", "README.md"), repository.show("main", "README.md"))
    }

    @Then("pull request {int} names main's latest commit as its merge_commit_sha")
    fun names(number: Int) {
        reads(number)
        assertEquals(repository.head("main"), read.path("merge_commit_sha").asText(), "$read")
    }

    private fun get(path: String, accept: String = HAL) = Request(Method.GET, path)
        .header("Authorization", "Bearer ${GitHubService.TOKEN}").header("Accept", accept)

    private companion object {
        const val HAL = "application/hal+json"
        const val HAL_FORMS = "application/prs.hal-forms+json"
    }
}
