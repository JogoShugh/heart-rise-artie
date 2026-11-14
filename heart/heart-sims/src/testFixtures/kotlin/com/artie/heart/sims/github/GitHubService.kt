package com.artie.heart.sims.github

import com.artie.heart.gateway.Dam
import com.artie.heart.gateway.fixtures.GitHubGateway
import com.artie.heart.gateway.fixtures.GitHubUpstream
import com.artie.heart.gateway.fixtures.Issue
import com.artie.heart.gateway.fixtures.Pull
import org.http4k.core.HttpHandler
import java.nio.file.Files
import java.nio.file.Path

/**
 * The GitHub service: heart-gateway's gateway, as shipped with its GitHub
 * connector, in front of the small GitHub of its own tests. It stands for an
 * API we do not own, so its shape is GitHub's. A reset starts a fresh small
 * GitHub with the seed: repository acme/web, pull request 482 by ada, issue 7.
 */
class GitHubService(
    private val connector: Path = findConnector(),
    /** Where acme/web's pull requests are kept, as branches, when they are kept for real; null for the seed's. */
    private val repository: GitRepository? = null,
) {
    @Volatile
    var upstream = GitHubUpstream()
        private set

    @Volatile
    private var gateway: HttpHandler = GitHubGateway.handler(upstream, connector)

    val handler: HttpHandler = { request -> gateway(request) }

    /** From now on, until a reset, the gateway stands with [dam]'s rungs in front of GitHub, for a client passing through. */
    fun dam(dam: Dam) {
        gateway = GitHubGateway.handler(upstream, connector, dam = dam)
    }

    /**
     * Once, until a reset: right after the issue [number] is next read on GitHub, a teammate changes it there, on
     * GitHub itself, as [change] says: its title, its state and state reason.
     */
    fun meddle(number: Long, change: Map<String, String>) {
        upstream.afterNextIssueRead[number] = { issue ->
            change["title"]?.let { issue.title = it }
            change["state"]?.let { issue.state = it }
            if ("state_reason" in change) issue.stateReason = change["state_reason"]
        }
    }

    /** GitHub has worked out whether pull request [number] can be merged: [mergeable], or null while it hasn't. */
    fun mergeable(number: Long, mergeable: Boolean?) {
        upstream.pulls.first { it.number == number }.mergeable = mergeable
    }

    fun reset() {
        upstream = GitHubUpstream().apply {
            callers[TOKEN] = AUTHOR
            val kept = repository
            if (kept == null) {
                pulls += Pull(OWNER, REPO, PULL, state = "open", title = "Speed up the nightly build", head = "3f2a9c1")
            } else {
                // Each pr-N branch is pull request N, titled by its commit, its head the branch's tip.
                kept.pullBranches().forEach { (number, branch) ->
                    pulls += Pull(OWNER, REPO, number, state = "open", title = kept.subject(branch), head = kept.head(branch))
                }
                merger = { pull, method -> kept.merge("pr-${pull.number}", method, "${pull.title} (#${pull.number})") }
            }
            issues += Issue(
                OWNER, REPO, ISSUE, title = "The nightly build is slow", state = "open", stateReason = null,
                locked = false, author = AUTHOR, labels = mutableListOf("ci"),
            )
        }
        gateway = GitHubGateway.handler(upstream, connector)
    }

    companion object {
        const val OWNER = "acme"
        const val REPO = "web"
        const val PULL = 482L
        const val ISSUE = 7L
        const val AUTHOR = "ada"

        /** The token the small GitHub knows as ada's. */
        const val TOKEN = "github-token"

        private val CONNECTOR = Path.of("heart", "heart-gateway", "connectors", "github")

        /** The shipped connector folder, found from wherever the simulator runs inside the repository. */
        fun findConnector(): Path {
            var at: Path? = Path.of("").toAbsolutePath()
            while (at != null) {
                listOf(at.resolve(CONNECTOR), at.resolve(CONNECTOR.subpath(1, CONNECTOR.nameCount)))
                    .firstOrNull { Files.isDirectory(it) }?.let { return it }
                at = at.parent
            }
            error("no heart-gateway/connectors/github above ${Path.of("").toAbsolutePath()}")
        }
    }
}
