package com.artie.heart.gateway.bdd

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
    val title: String,
    var head: String = "0000000",
    var merged: Boolean = false,
    var draft: Boolean = false,
    /** Null while GitHub is still working it out. */
    var mergeable: Boolean? = true,
    var mergeableState: String = "clean",
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
    val requests = mutableListOf<Request>()
    val last: Request? get() = requests.lastOrNull()

    /** Pull requests GitHub won't merge, with what it says. */
    val unmergeable = mutableMapOf<Long, String>()

    /** Repositories, `owner/repo`, an anonymous caller can't push to. */
    val readOnly = mutableSetOf<String>()

    /** The callers GitHub knows, by token; with none, it takes any request, as for a public repository. */
    val callers = mutableMapOf<String, String>()

    /** `caller owner/repo`: repositories a known caller can't push to. */
    val readOnlyFor = mutableSetOf<String>()

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
                mapper.readTree(request.bodyString()).path("state").asText(null)?.let { pull.state = it }
                json(Response(Status.OK), body(pull))
            } ?: Response(Status.NOT_FOUND)
        },
        "/repos/{owner}/{repo}" bind Method.GET to { request ->
            val name = "${request.path("owner")}/${request.path("repo")}"
            val push = name !in readOnly && "${caller(request)} $name" !in readOnlyFor
            json(Response(Status.OK), repository(name, push))
        },
        "/repos/{owner}/{repo}/pulls" bind Method.GET to { request ->
            val state = request.query("state") ?: "open"
            val listed = pulls.filter { it.owner == request.path("owner") && it.repo == request.path("repo") }
                .filter { state == "all" || it.state == state }
            json(Response(Status.OK), listed.map { body(it) })
        },
    )

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
        put("merged", pull.merged)
        put("draft", pull.draft)
        pull.mergeable?.let { put("mergeable", it) } ?: putNull("mergeable")
        put("mergeable_state", pull.mergeableState)
        (path("head") as ObjectNode).put("sha", pull.head)
        val repo = path("base").path("repo") as ObjectNode
        repo.put("name", pull.repo).put("full_name", "${pull.owner}/${pull.repo}")
        (repo.path("owner") as ObjectNode).put("login", pull.owner)
    }

    /** GitHub's own example repository, as [name] and with whether the caller may [push]. */
    private fun repository(name: String, push: Boolean): JsonNode = REPOSITORY.deepCopy().apply {
        put("full_name", name)
        put("name", name.substringAfter('/'))
        (path("owner") as ObjectNode).put("login", name.substringBefore('/'))
        (path("permissions") as ObjectNode).put("push", push)
    }

    private companion object {
        const val ID_BASE = 1_000_000L

        /** GitHub's own examples, from its description (github/rest-api-description, MIT licensed). */
        val PULL: ObjectNode = example("/github/pull-request.json")
        val REPOSITORY: ObjectNode = example("/github/repository.json")

        private fun example(path: String): ObjectNode =
            ObjectMapper().readTree(checkNotNull(GitHubUpstream::class.java.getResource(path))) as ObjectNode

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
