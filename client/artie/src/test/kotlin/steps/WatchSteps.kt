package steps

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** script-watch.feature: a script that keeps watching the small GitHub, in process. */
class WatchSteps(private val gateway: GitHubGatewaySteps, private val scripts: ScriptSteps) {
    @Given("pull request {long} is a draft on the small GitHub")
    fun draft(number: Long) {
        gateway.github.pulls.first { it.number == number }.draft = true
    }

    @Given("pull request {long} is a draft on the small GitHub until it is next read")
    fun draftUntilRead(number: Long) {
        draft(number)
        gateway.github.afterNextRead[number] = { it.draft = false }
    }

    @Given("pull request {long} is labelled {string} on the small GitHub")
    fun labelled(number: Long, label: String) {
        gateway.github.pulls.first { it.number == number }.labels += label
    }

    @Given("issue {long} of {string} is closed on the small GitHub once pull request {long} is closed")
    fun closedWith(issue: Long, repository: String, pull: Long) {
        gateway.github.afterPullChange += { changed ->
            if (changed.number == pull && changed.state == "closed") {
                gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == issue }
                    .state = "closed"
            }
        }
    }

    @Given("issue {long} of {string} is closed on the small GitHub once issue {long} has a new comment")
    fun closedWithComment(issue: Long, repository: String, commented: Long) {
        gateway.github.afterComment += { comment ->
            if (comment.issue == commented && "${comment.owner}/${comment.repo}" == repository) {
                gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == issue }
                    .state = "closed"
            }
        }
    }

    @Given(
        "issue {long} of {string} is closed on the small GitHub, whatever happens, once its pull requests are listed " +
            "{int} times",
    )
    fun closedAfterLists(issue: Long, repository: String, times: Int) {
        var listed = 0
        gateway.github.beforeAnswer += { request ->
            if (request.method.name == "GET" && request.uri.path == "/repos/$repository/pulls" && ++listed >= times) {
                gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == issue }
                    .state = "closed"
            }
        }
    }

    @Given(
        "issue {long} of {string} is closed on the small GitHub, whatever happens, once its issues are listed " +
            "{int} times",
    )
    fun closedAfterIssueLists(issue: Long, repository: String, times: Int) {
        var listed = 0
        gateway.github.beforeAnswer += { request ->
            if (request.method.name == "GET" && request.uri.path == "/repos/$repository/issues" && ++listed >= times) {
                gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == issue }
                    .state = "closed"
            }
        }
    }

    /** Released when the pull request the held answer waits on is merged; whether it was, before the wait ran out. */
    private val merged = java.util.concurrent.CountDownLatch(1)
    private var heldUntilMerged: Boolean? = null

    /** Done once the held answer is given, whether it waited long enough or not. */
    private val onceGiven = mutableListOf<() -> Unit>()

    @Given(
        "the small GitHub holds its first answer to {string} until pull request {long} is merged, " +
            "at most {int} seconds",
    )
    fun holdsAnswer(path: String, pull: Long, seconds: Int) = holds(path, pull, seconds) { true }

    @Given(
        "the small GitHub holds its first answer to {string} after pull request {long} is merged, until pull " +
            "request {long} is merged, at most {int} seconds",
    )
    fun holdsAnswerAfter(path: String, after: Long, pull: Long, seconds: Int) {
        val before = java.util.concurrent.CountDownLatch(1)
        gateway.github.beforeAnswer += { request ->
            if (request.method.name == "PUT" && request.uri.path.endsWith("/pulls/$after/merge")) before.countDown()
        }
        holds(path, pull, seconds) { before.count == 0L }
    }

    @Given("issue {long} of {string} is closed on the small GitHub once that held answer is given")
    fun closedOnceGiven(issue: Long, repository: String) {
        onceGiven += {
            gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == issue }
                .state = "closed"
        }
    }

    /**
     * The first answer to [path] once [ready] says so, held until pull request [pull] is merged, or [seconds] have
     * passed; then whatever waits on its being given.
     */
    private fun holds(path: String, pull: Long, seconds: Int, ready: () -> Boolean) {
        var held = false
        gateway.github.beforeAnswer += { request ->
            val name = request.method.name
            if (name == "PUT" && request.uri.path.endsWith("/pulls/$pull/merge")) merged.countDown()
            val heldNow = name == "GET" && request.uri.path == path
            if (heldNow && !held && ready()) {
                held = true
                heldUntilMerged = merged.await(seconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                onceGiven.forEach { it() }
            }
        }
    }

    @Then("the small GitHub answered {string} the first time only after pull request {long} was merged")
    fun answeredAfterMerge(path: String, pull: Long) {
        assertEquals(true, heldUntilMerged, "the first answer to $path waited for pull request $pull's merge")
    }

    @Then("the small GitHub gave that held answer only after pull request {long} was merged")
    fun heldAnswerAfterMerge(pull: Long) {
        assertEquals(true, heldUntilMerged, "the held answer waited for pull request $pull's merge")
    }

    @Given(
        "{string} comments {string} on issue {long} of {string} on the small GitHub once its issues are listed " +
            "{int} times",
    )
    fun commentsLater(author: String, body: String, issue: Long, repository: String, times: Int) {
        val (owner, repo) = repository.split("/")
        var listed = 0
        gateway.github.beforeAnswer += { request ->
            val listing = request.method.name == "GET" && request.uri.path == "/repos/$repository/issues"
            if (listing && ++listed == times) {
                val id = (gateway.github.comments.maxOfOrNull { it.id } ?: 0) + 1
                gateway.github.comments +=
                    com.artie.heart.gateway.fixtures.Comment(owner, repo, issue, id, author, body)
            }
        }
    }

    @Given(
        "{string} comments {string} on issue {long} of {string} on the small GitHub once its comments are listed " +
            "{int} times",
    )
    fun commentsAfterThread(author: String, body: String, issue: Long, repository: String, times: Int) {
        val (owner, repo) = repository.split("/")
        val listed = java.util.concurrent.atomic.AtomicInteger()
        gateway.github.beforeAnswer += { request ->
            val path = "/repos/$repository/issues/$issue/comments"
            if (request.method.name == "GET" && request.uri.path == path && listed.incrementAndGet() == times) {
                val id = (gateway.github.comments.maxOfOrNull { it.id } ?: 0) + 1
                gateway.github.comments +=
                    com.artie.heart.gateway.fixtures.Comment(owner, repo, issue, id, author, body)
            }
        }
    }

    @Given(
        "{string} comments {string} on issue {long} of {string} on the small GitHub {int} milliseconds after its " +
            "comments are first listed, and GitHub sends its webhook",
    )
    fun commentsLaterWithWebhook(author: String, body: String, issue: Long, repository: String, after: Long) {
        val (owner, repo) = repository.split("/")
        val listed = java.util.concurrent.atomic.AtomicBoolean()
        gateway.github.beforeAnswer += { request ->
            val path = "/repos/$repository/issues/$issue/comments"
            if (request.method.name == "GET" && request.uri.path == path && listed.compareAndSet(false, true)) {
                Thread.ofVirtual().start {
                    Thread.sleep(after)
                    val id = (gateway.github.comments.maxOfOrNull { it.id } ?: 0) + 1
                    gateway.github.comments +=
                        com.artie.heart.gateway.fixtures.Comment(owner, repo, issue, id, author, body)
                    checkNotNull(gateway.handler)(webhook(repository, issue, id, checkNotNull(gateway.webhookSecret)))
                }
            }
        }
    }

    /** GitHub's webhook for a comment, [id], made on [issue], its body signed with [secret]. */
    private fun webhook(repository: String, issue: Long, id: Long, secret: String): org.http4k.core.Request {
        val api = "https://api.github.com/repos/$repository"
        val body = """{"action":"created","issue":{"url":"$api/issues/$issue"},""" +
            """"comment":{"url":"$api/issues/comments/$id"},"repository":{"url":"$api"}}"""
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            .apply { init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val signature = mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
        return org.http4k.core.Request(org.http4k.core.Method.POST, "/hooks/github")
            .header("X-Hub-Signature-256", "sha256=$signature").body(body)
    }

    @Given(
        "issue {long} of {string}, titled {string}, opens on the small GitHub once its issues are listed {int} times",
    )
    fun opensLater(number: Long, repository: String, title: String, times: Int) {
        val (owner, repo) = repository.split("/")
        val listed = java.util.concurrent.atomic.AtomicInteger()
        gateway.github.beforeAnswer += { request ->
            val listing = request.method.name == "GET" && request.uri.path == "/repos/$repository/issues"
            if (listing && listed.incrementAndGet() == times) {
                gateway.github.issues += com.artie.heart.gateway.fixtures.Issue(
                    owner, repo, number, title, "open", stateReason = null, locked = false, author = "ada",
                )
            }
        }
    }

    @Then("issue {long} of {string} is assigned to {string} on the small GitHub")
    fun assigned(number: Long, repository: String, login: String) {
        val issue = gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == number }
        assertEquals(listOf(login), issue.assignees)
    }

    @Given("issue {long} of {string} is labelled {string} on the small GitHub")
    fun issueLabelled(number: Long, repository: String, label: String) {
        gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == number }.labels += label
    }

    /** A pull request is an issue too, numbered as it is: its label is on either, as the small GitHub keeps them. */
    @Then("pull request {long} of {string} is labelled {string} on the small GitHub")
    fun pullLabelledNow(number: Long, repository: String, label: String) {
        fun mine(owner: String, repo: String, at: Long) = "$owner/$repo" == repository && at == number
        val labels = gateway.github.pulls.filter { mine(it.owner, it.repo, it.number) }.flatMap { it.labels } +
            gateway.github.issues.filter { mine(it.owner, it.repo, it.number) }.flatMap { it.labels }
        assertTrue(label in labels, "$labels")
    }

    @Then("pull request {long} of {string} is closed and not merged on the small GitHub")
    fun closedNotMerged(number: Long, repository: String) {
        val pull = gateway.github.pulls.first { "${it.owner}/${it.repo}" == repository && it.number == number }
        assertEquals("closed false", "${pull.state} ${pull.merged}")
    }

    @Then("pull request {long} of {string} is open on the small GitHub")
    fun open(number: Long, repository: String) {
        val pull = gateway.github.pulls.first { "${it.owner}/${it.repo}" == repository && it.number == number }
        assertEquals("open", pull.state)
    }

    @Given("the small GitHub answers the {int}nd read of {string} with 500")
    fun failsRead(n: Int, path: String) {
        gateway.github.failRead[path] = n
    }

    @Then("the small GitHub was asked to merge these pull requests, in order")
    fun merged(table: DataTable) {
        val expected = table.asMaps().mapNotNull { it["number"]?.takeIf(String::isNotBlank)?.toLong() }
        val asked = gateway.github.requests.filter { it.method.name == "PUT" }
            .mapNotNull { MERGE.matchEntire(it.uri.path)?.groupValues?.get(1)?.toLong() }
        assertEquals(expected, asked)
    }

    @Then("the run noted")
    fun noted(table: DataTable) {
        val expected = table.asMaps().mapNotNull { it["note"]?.takeIf(String::isNotBlank) }
        assertEquals(expected, checkNotNull(scripts.lastRun).notes)
    }

    @Then("the run noted one note beginning {string}")
    fun notedOne(beginning: String) {
        val notes = checkNotNull(scripts.lastRun).notes
        assertEquals(1, notes.count { it.startsWith(beginning) }, notes.toString())
    }

    private companion object {
        val MERGE = Regex("""/repos/[^/]+/[^/]+/pulls/(\d+)/merge""")
    }
}
