package com.artie.heart.binding

import com.artie.heart.Acted
import com.artie.heart.Caller
import com.artie.heart.CallerForbiddenException
import com.artie.heart.CallerUnauthorizedException
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Embedded
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.PullRequest
import com.artie.heart.fixtures.PullRequests
import java.util.concurrent.ConcurrentHashMap

/** The pull requests a binding's test server holds, by owner, repository and number. */
class PullStore {
    val pulls = ConcurrentHashMap<Triple<String, String, Long>, PullRequest>()

    /** The credential the last read, act or search was handed (callers.feature). */
    @Volatile
    var lastCaller: String? = null

    fun put(pull: PullRequest) {
        pulls[key(pull)] = pull
    }

    fun key(pull: PullRequest) = Triple(pull.owner, pull.repo, pull.number)
}

/**
 * Beside the lamp, a type whose item path is more than `/{collection}/{id}`:
 * a pull request lives at `/repos/{owner}/{repo}/pulls/{number}`
 * (item-paths.feature), and a repository's pull requests are listed at
 * `/repos/{owner}/{repo}/pulls`, filtered by state (declared-searches.feature).
 * It is read and found by those fields, never by an id alone.
 */
object PullServer {
    fun resource(store: PullStore): HeartResource<PullRequest, String> = HeartResource(
        layers = Layers(
            PullRequests.type(
                finding = PullRequests.finding(),
                subCollections = mapOf("stack" to STACK),
                // Its author is another resource, embedded (embedded.feature).
                embedded = listOf(Embedded("author", "/author", AUTHOR)),
            ),
        ),
        loadAt = { at ->
            store.lastCaller = Caller.authorization()
            refuseIn(at.getValue("repo"))
            val number = at.getValue("number").toLongOrNull()
            number?.let { store.pulls[Triple(at.getValue("owner"), at.getValue("repo"), it)] }
                ?: throw ResourceNotFoundException("no pull request at $at")
        },
        perform = { pull, act, _ ->
            store.lastCaller = Caller.authorization()
            // Splitting makes a new pull request elsewhere, and leaves this one as it was (creating-acts.feature).
            if (act == "split") {
                val split = pull.copy(number = pull.number + SPLIT_OFFSET, version = 1)
                store.put(split)
                return@HeartResource Acted(pull, created = "/repos/${split.owner}/${split.repo}/pulls/${split.number}")
            }
            val next = pull.copy(state = if (act == "close") "closed" else "open", version = pull.version + 1)
            if (!store.pulls.replace(store.key(pull), pull, next)) {
                throw ConcurrentWriteException("pull request ${pull.number} was written first")
            }
            Acted(next)
        },
        search = { values ->
            store.lastCaller = Caller.authorization()
            store.pulls.values
                .filter { it.owner == values["owner"] && it.repo == values["repo"] }
                .filter { values["state"] == null || it.state == values["state"] }
                .sortedBy { it.number }
                .map { Found(it.number, "Pull request ${it.number}", it.variables()) }
        },
        // A pull request's stack: the one split off it (sub-collections.feature).
        lists = mapOf(
            "stack" to { at ->
                val (owner, repo) = at.getValue("owner") to at.getValue("repo")
                val number = at.getValue("number").toLongOrNull()
                    ?.takeIf { store.pulls.containsKey(Triple(owner, repo, it)) }
                    ?: throw ResourceNotFoundException("no pull request at $at")
                listOfNotNull(store.pulls[Triple(owner, repo, number + SPLIT_OFFSET)])
                    .map { Found(it.number, "Pull request ${it.number}", it.variables()) }
            },
        ),
    )

    /** Two repositories refuse every caller: one finds the credential no longer good, one won't show itself. */
    private fun refuseIn(repo: String) {
        if (repo == "expired") throw CallerUnauthorizedException("the credential has expired")
        if (repo == "secret") throw CallerForbiddenException("this repository is not yours to see")
    }

    private const val SPLIT_OFFSET = 1000L
    private const val STACK = "/repos/{owner}/{repo}/pulls/{number}/stack"
    private val AUTHOR = ItemPath("/users/{login}", mapOf("login" to "/login"))

    private fun PullRequest.variables() = mapOf("owner" to owner, "repo" to repo, "number" to "$number")
}
