package com.artie.heart.gateway.fixtures

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.security.MessageDigest

/** A pull request as the small GitHub keeps it. */
data class Pull(
    val owner: String,
    val repo: String,
    val number: Long,
    var state: String,
    var title: String,
    var head: String = "0000000",
    var merged: Boolean = false,
    var draft: Boolean = false,
    /** Null while GitHub is still working it out. */
    var mergeable: Boolean? = true,
    var mergeableState: String = "clean",
    var body: String = "",
    /** Whether the base has commits the branch doesn't: only then can GitHub update the branch. */
    var behind: Boolean = false,
    val requestedReviewers: MutableList<String> = mutableListOf(),
)

/** An issue as the small GitHub keeps it. */
data class Issue(
    val owner: String,
    val repo: String,
    val number: Long,
    var title: String,
    var state: String,
    var stateReason: String?,
    var locked: Boolean,
    val author: String,
    var body: String = "",
    val labels: MutableList<String> = mutableListOf(),
    val assignees: MutableList<String> = mutableListOf(),
    var milestone: Int? = null,
    /** The numbers of its sub-issues, in order. */
    val subIssues: MutableList<Long> = mutableListOf(),
)

/** A comment on an issue, as the small GitHub keeps it. */
data class Comment(
    val owner: String,
    val repo: String,
    val issue: Long,
    val id: Long,
    val author: String,
    var body: String,
)

/**
 * A small GitHub, in memory, answering as GitHub does: pulls/get as one JSON
 * object, pulls/list as a bare array whose state filter defaults to open,
 * pulls/update by PATCH, and pulls/merge by PUT, which checks the `sha` it is
 * given against the head (409 when they differ) and refuses a pull request
 * it can't merge (405). It keeps every request, to show what the gateway asked.
 */
class GitHubUpstream {
    val pulls = mutableListOf<Pull>()
    val issues = mutableListOf<Issue>()
    val comments = mutableListOf<Comment>()
    val requests = mutableListOf<Request>()
    val last: Request? get() = requests.lastOrNull()

    /** Pull requests GitHub won't merge, with what it says. */
    val unmergeable = mutableMapOf<Long, String>()

    /** Repositories, `owner/repo`, an anonymous caller can't push to. */
    val readOnly = mutableSetOf<String>()

    /** The callers GitHub knows, by token; with none, it takes any request, as for a public repository. */
    val callers = mutableMapOf<String, String>()

    /** Each known caller's access to every repository: read, triage or write; write unless a scenario says. */
    val access = mutableMapOf<String, String>()

    /** `caller owner/repo`: repositories a known caller can't push to. */
    val readOnlyFor = mutableSetOf<String>()

    /** Repositories, `owner/repo`, with issues turned off. */
    val issuesOff = mutableSetOf<String>()

    /** Repositories, `owner/repo`, that are archived. */
    val archived = mutableSetOf<String>()

    /** The status GitHub answered each request with, in order. */
    val statuses = mutableListOf<Int>()

    /** Heads that move just before GitHub merges: another push lands first. */
    val movesBeforeMerge = mutableMapOf<Long, String>()

    private val mapper = ObjectMapper()

    val handler: HttpHandler = { request: Request ->
        requests += request
        answer(request).also { statuses += it.status.code }
    }

    /** A token GitHub doesn't know is refused; a GET whose If-None-Match names the body's ETag is a 304. */
    private fun answer(request: Request): Response {
        val token = request.header("Authorization")?.removePrefix("Bearer ")
        if (callers.isNotEmpty() && (token == null || token !in callers)) {
            return message(Status.UNAUTHORIZED, "Bad credentials")
        }
        return tagged(request, routes(request))
    }

    private fun tagged(request: Request, response: Response): Response {
        if (request.method != Method.GET || !response.status.successful) return response
        val etag = "\"" + sha256(response.bodyString()) + "\""
        return if (request.header("If-None-Match") == etag) {
            Response(Status.NOT_MODIFIED).header("ETag", etag)
        } else {
            response.header("ETag", etag)
        }
    }

    private fun caller(request: Request): String? =
        request.header("Authorization")?.removePrefix("Bearer ")?.let { callers[it] }

    private val routes = routes(
        "/repos/{owner}/{repo}/pulls/{number}/merge" bind Method.PUT to { request ->
            pull(request)?.let { merge(it, request) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/pulls/{number}" bind Method.GET to { request ->
            pull(request)?.let { json(Response(Status.OK), body(it)) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/pulls/{number}" bind Method.PATCH to { request ->
            pull(request)?.let { pull ->
                val change = mapper.readTree(request.bodyString())
                change.path("state").asText(null)?.let { pull.state = it }
                change.path("title").asText(null)?.let { pull.title = it }
                change.path("body").asText(null)?.let { pull.body = it }
                json(Response(Status.OK), body(pull))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/pulls/{number}/requested_reviewers" bind Method.POST to { request ->
            pull(request)?.let { requestReviews(it, request) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/pulls/{number}/update-branch" bind Method.PUT to { request ->
            pull(request)?.let { updateBranch(it, request) } ?: Response(Status.NOT_FOUND)
        },
        "/user" bind Method.GET to { request ->
            caller(request)?.let { json(Response(Status.OK), USER.deepCopy().put("login", it)) }
                ?: message(Status.UNAUTHORIZED, "Requires authentication")
        },
        "/repos/{owner}/{repo}" bind Method.GET to { request ->
            val name = "${request.path("owner")}/${request.path("repo")}"
            val caller = caller(request)
            val level = access[caller] ?: "write"
            val push = name !in readOnly && "$caller $name" !in readOnlyFor && level == "write"
            json(Response(Status.OK), repository(name, push, triage = push || level == "triage"))
        },
        // As GitHub does: the new issue takes the repository's next number, and its author is the caller.
        "/repos/{owner}/{repo}/issues" bind Method.POST to { request ->
            val (owner, repo) = request.path("owner")!! to request.path("repo")!!
            val change = mapper.readTree(request.bodyString())
            val numbers = issues.filter { it.owner == owner && it.repo == repo }.map { it.number } +
                pulls.filter { it.owner == owner && it.repo == repo }.map { it.number }
            val issue = Issue(
                owner, repo, (numbers.maxOrNull() ?: 0) + 1, change.path("title").asText(), "open", null,
                locked = false, author = caller(request) ?: "octocat", body = change.path("body").asText(""),
                labels = change.path("labels").map { it.asText() }.toMutableList(),
                assignees = change.path("assignees").map { it.asText() }.toMutableList(),
            )
            issues += issue
            json(Response(Status.CREATED), issueBody(issue))
                .header("Location", "https://api.github.com/repos/$owner/$repo/issues/${issue.number}")
        },
        "/repos/{owner}/{repo}/issues/{number}" bind Method.GET to { request ->
            issue(request)?.let { json(Response(Status.OK), issueBody(it)) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}" bind Method.PATCH to { request ->
            issue(request)?.let { issue ->
                val change = mapper.readTree(request.bodyString())
                change.path("state").asText(null)?.let { issue.state = it }
                if (change.has("state_reason")) issue.stateReason = change.path("state_reason").asText(null)
                change.path("title").asText(null)?.let { issue.title = it }
                change.path("body").asText(null)?.let { issue.body = it }
                if (change.has("milestone")) {
                    issue.milestone = change.path("milestone").takeUnless { it.isNull }?.asInt()
                }
                json(Response(Status.OK), issueBody(issue))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/labels" bind Method.POST to { request ->
            issue(request)?.let { issue ->
                issue.labels += mapper.readTree(request.bodyString()).path("labels").map { it.asText() }
                json(Response(Status.OK), issue.labels.map { mapOf("name" to it) })
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/labels/{name}" bind Method.DELETE to { request ->
            issue(request)?.let { issue ->
                if (issue.labels.remove(request.path("name"))) {
                    json(Response(Status.OK), issue.labels.map { mapOf("name" to it) })
                } else {
                    message(Status.NOT_FOUND, "Label does not exist")
                }
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/assignees" bind Method.POST to { request ->
            issue(request)?.let { issue ->
                issue.assignees += mapper.readTree(request.bodyString()).path("assignees").map { it.asText() }
                json(Response(Status.CREATED), issueBody(issue))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/assignees" bind Method.DELETE to { request ->
            issue(request)?.let { issue ->
                issue.assignees -= mapper.readTree(request.bodyString()).path("assignees").map { it.asText() }.toSet()
                json(Response(Status.OK), issueBody(issue))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/lock" bind Method.PUT to { request ->
            issue(request)?.let { it.locked = true; Response(Status.NO_CONTENT) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/lock" bind Method.DELETE to { request ->
            issue(request)?.let { it.locked = false; Response(Status.NO_CONTENT) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/comments" bind Method.GET to { request ->
            issue(request)?.let { issue ->
                json(Response(Status.OK), comments.filter { it.on(issue) }.map { commentBody(it) })
            } ?: Response(Status.NOT_FOUND)
        },
        // As GitHub does: the new comment's author is the caller, and its id the next of the repository's.
        "/repos/{owner}/{repo}/issues/{number}/comments" bind Method.POST to { request ->
            issue(request)?.let { issue ->
                val id = (comments.maxOfOrNull { it.id } ?: 0) + 1
                val body = mapper.readTree(request.bodyString()).path("body").asText()
                val comment = Comment(issue.owner, issue.repo, issue.number, id, caller(request) ?: "octocat", body)
                comments += comment
                json(Response(Status.CREATED), commentBody(comment))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/comments/{id}" bind Method.GET to { request ->
            comment(request)?.let { json(Response(Status.OK), commentBody(it)) } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/comments/{id}" bind Method.PATCH to { request ->
            comment(request)?.let { comment ->
                comment.body = mapper.readTree(request.bodyString()).path("body").asText()
                json(Response(Status.OK), commentBody(comment))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/sub_issues" bind Method.GET to { request ->
            issue(request)?.let { parent ->
                json(Response(Status.OK), parent.subIssues.map { n -> issueBody(issues.first { it.number == n }) })
            } ?: Response(Status.NOT_FOUND)
        },
        // As GitHub does: a sub-issue is named by its id, not its number; the answer is the parent.
        "/repos/{owner}/{repo}/issues/{number}/sub_issues" bind Method.POST to { request ->
            issue(request)?.let { parent ->
                val id = mapper.readTree(request.bodyString()).path("sub_issue_id").asLong()
                val child = issues.firstOrNull { ISSUE_BASE + it.number == id }
                    ?: return@let message(Status.UNPROCESSABLE_ENTITY, "Validation Failed")
                parent.subIssues += child.number
                json(Response(Status.CREATED), issueBody(parent))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}/issues/{number}/sub_issue" bind Method.DELETE to { request ->
            issue(request)?.let { parent ->
                val id = mapper.readTree(request.bodyString()).path("sub_issue_id").asLong()
                parent.subIssues.removeIf { ISSUE_BASE + it == id }
                json(Response(Status.OK), issueBody(parent))
            } ?: Response(Status.NOT_FOUND)
        },
        // As GitHub does: a repository's issues, and its pull requests too, each an issue with a pull_request member.
        "/repos/{owner}/{repo}/issues" bind Method.GET to { request ->
            val state = request.query("state") ?: "open"
            val inRepo = { owner: String, repo: String ->
                owner == request.path("owner") && repo == request.path("repo")
            }
            val listed = issues.filter { inRepo(it.owner, it.repo) && (state == "all" || it.state == state) }
                .map { issueBody(it) } +
                pulls.filter { inRepo(it.owner, it.repo) && (state == "all" || it.state == state) }
                    .map { pullAsIssue(it) }
            json(Response(Status.OK), listed)
        },
        "/repos/{owner}/{repo}/pulls" bind Method.GET to { request ->
            val state = request.query("state") ?: "open"
            val listed = pulls.filter { it.owner == request.path("owner") && it.repo == request.path("repo") }
                .filter { state == "all" || it.state == state }
            json(Response(Status.OK), listed.map { body(it) })
        },
    )

    /** As GitHub does: only collaborators can be asked, and the answer is the pull request. */
    private fun requestReviews(pull: Pull, request: Request): Response {
        val reviewers = mapper.readTree(request.bodyString()).path("reviewers").map { it.asText() }
        if (reviewers.any { it !in callers.values }) {
            return message(
                Status.UNPROCESSABLE_ENTITY,
                "Reviews may only be requested from collaborators. One or more of the users or teams you specified " +
                    "is not a collaborator of the ${pull.owner}/${pull.repo} repository.",
            )
        }
        pull.requestedReviewers += reviewers
        return json(Response(Status.CREATED), body(pull))
    }

    /** As GitHub does: 422 when the head moved or there's nothing to bring in; else 202, and the head moves on. */
    private fun updateBranch(pull: Pull, request: Request): Response {
        val expected = mapper.readTree(request.bodyString()).path("expected_head_sha").asText(null)
        return when {
            expected != null && expected != pull.head ->
                message(Status.UNPROCESSABLE_ENTITY, "expected head sha didn't match current head ref.")
            !pull.behind -> message(Status.UNPROCESSABLE_ENTITY, "There are no new commits on the base branch.")
            else -> {
                pull.behind = false
                pull.head = "updated-${pull.head}"
                json(Response(Status.ACCEPTED), mapOf("message" to "Updating pull request branch."))
            }
        }
    }

    private fun merge(pull: Pull, request: Request): Response {
        movesBeforeMerge.remove(pull.number)?.let { pull.head = it }
        val sha = mapper.readTree(request.bodyString()).path("sha").asText(null)
        return when {
            pull.number in unmergeable -> message(Status.METHOD_NOT_ALLOWED, unmergeable.getValue(pull.number))
            sha != null && sha != pull.head ->
                message(Status.CONFLICT, "Head branch was modified. Review and try the merge again.")
            else -> {
                pull.state = "closed"
                pull.merged = true
                json(Response(Status.OK), mapOf("sha" to "merge-${pull.head}", "merged" to true, "message" to "Merged"))
            }
        }
    }

    private fun issue(request: Request): Issue? = issues.firstOrNull {
        it.owner == request.path("owner") && it.repo == request.path("repo") &&
            it.number.toString() == request.path("number")
    }

    private fun comment(request: Request): Comment? = comments.firstOrNull {
        it.owner == request.path("owner") && it.repo == request.path("repo") && it.id.toString() == request.path("id")
    }

    private fun Comment.on(issue: Issue) = owner == issue.owner && repo == issue.repo && this.issue == issue.number

    /** GitHub's own example comment, as [comment]: its URL names its owner and repository, nothing else does. */
    private fun commentBody(comment: Comment): JsonNode = COMMENT.deepCopy().apply {
        val api = "https://api.github.com/repos/${comment.owner}/${comment.repo}"
        put("id", comment.id)
        put("body", comment.body)
        put("url", "$api/issues/comments/${comment.id}")
        put("issue_url", "$api/issues/${comment.issue}")
        (path("user") as ObjectNode).put("login", comment.author)
    }

    /** GitHub's own example issue, as [issue]: its URL names its owner and repository, nothing else does. */
    private fun issueBody(issue: Issue): JsonNode = ISSUE.deepCopy().apply {
        val url = "https://api.github.com/repos/${issue.owner}/${issue.repo}/issues/${issue.number}"
        put("id", ISSUE_BASE + issue.number)
        put("number", issue.number)
        put("title", issue.title)
        put("body", issue.body)
        put("state", issue.state)
        issue.stateReason?.let { put("state_reason", it) } ?: putNull("state_reason")
        put("locked", issue.locked)
        if (issue.locked) put("active_lock_reason", "too heated") else putNull("active_lock_reason")
        put("url", url)
        put("repository_url", "https://api.github.com/repos/${issue.owner}/${issue.repo}")
        (path("user") as ObjectNode).put("login", issue.author)
        putArray("labels").apply { issue.labels.forEach { addObject().put("name", it) } }
        putArray("assignees").apply { issue.assignees.forEach { addObject().put("login", it) } }
        issue.milestone?.let { putObject("milestone").put("number", it) } ?: putNull("milestone")
        remove("pull_request")
        remove("repository")
    }

    /** A pull request as GitHub lists it among a repository's issues. */
    private fun pullAsIssue(pull: Pull): JsonNode = ISSUE.deepCopy().apply {
        put("number", pull.number)
        put("title", pull.title)
        put("state", pull.state)
        put("url", "https://api.github.com/repos/${pull.owner}/${pull.repo}/issues/${pull.number}")
        val api = "https://api.github.com/repos/${pull.owner}/${pull.repo}"
        putObject("pull_request").put("url", "$api/pulls/${pull.number}")
    }

    private fun pull(request: Request): Pull? = pulls.firstOrNull {
        it.owner == request.path("owner") && it.repo == request.path("repo") &&
            it.number.toString() == request.path("number")
    }

    private fun message(status: Status, message: String): Response = json(Response(status), mapOf("message" to message))

    private fun json(response: Response, body: Any): Response =
        response.header("Content-Type", "application/json; charset=utf-8").body(mapper.writeValueAsString(body))

    /** GitHub's own example pull request, from its description, with the fields of [pull] set. */
    private fun body(pull: Pull): JsonNode = PULL.deepCopy().apply {
        put("id", ID_BASE + pull.number)
        put("number", pull.number)
        put("state", pull.state)
        put("title", pull.title)
        put("body", pull.body)
        putArray("requested_reviewers").apply { pull.requestedReviewers.forEach { addObject().put("login", it) } }
        put("merged", pull.merged)
        put("draft", pull.draft)
        // As GitHub does: a closed pull request's mergeability is never worked out, merged or not.
        val open = pull.state == "open"
        pull.mergeable?.takeIf { open }?.let { put("mergeable", it) } ?: putNull("mergeable")
        put("mergeable_state", if (open) pull.mergeableState else "unknown")
        (path("head") as ObjectNode).put("sha", pull.head)
        val repo = path("base").path("repo") as ObjectNode
        repo.put("name", pull.repo).put("full_name", "${pull.owner}/${pull.repo}")
        (repo.path("owner") as ObjectNode).put("login", pull.owner)
    }

    /** GitHub's own example repository, as [name] and with whether the caller may [push]. */
    private fun repository(name: String, push: Boolean, triage: Boolean): JsonNode = REPOSITORY.deepCopy().apply {
        put("full_name", name)
        put("name", name.substringAfter('/'))
        (path("owner") as ObjectNode).put("login", name.substringBefore('/'))
        put("has_issues", name !in issuesOff)
        put("archived", name in archived)
        // As GitHub says them: admin, maintain, push, triage, pull.
        putObject("permissions").put("admin", false).put("maintain", false)
            .put("push", push).put("triage", triage).put("pull", true)
    }

    private companion object {
        const val ID_BASE = 1_000_000L

        /** GitHub's own examples, from its description (github/rest-api-description, MIT licensed). */
        val PULL: ObjectNode = example("/github/pull-request.json")
        val REPOSITORY: ObjectNode = example("/github/repository.json")
        val ISSUE: ObjectNode = example("/github/issue.json")
        val USER: ObjectNode = example("/github/user.json")
        val COMMENT: ObjectNode = example("/github/issue-comment.json")
        const val ISSUE_BASE = 2_000_000L

        private fun example(path: String): ObjectNode =
            ObjectMapper().readTree(checkNotNull(GitHubUpstream::class.java.getResource(path))) as ObjectNode

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
