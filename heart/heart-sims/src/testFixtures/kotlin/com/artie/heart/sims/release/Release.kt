package com.artie.heart.sims.release

import com.artie.heart.sims.ci.CiKey
import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.ci.JenkinsHttp
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.github.GitRepository
import com.artie.heart.sims.rise.Answerer
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.Keys
import com.artie.heart.sims.rise.OfferWatcher
import com.artie.heart.sims.rise.ReplyService
import com.artie.heart.sims.rise.WatcherKey
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The merge watch release's parties, each a server at its own origin on [host]:
 *
 * | Port | Party |
 * |---|---|
 * | 8098 | the GitHub gateway, its small GitHub keeping acme/web's pull requests in the git repository at [repository] |
 * | 8099 | the offer watch, looking every few seconds |
 * | 8160 | ARTIE's replies |
 * | 8170 | the CI wrapper, in front of the real Jenkins on 8171 (exhibits/merge-watch-release/jenkins) |
 * | 8190 | the environments, Dev among them |
 */
class Release(
    private val host: String,
    repository: Path,
    jenkinsUser: String,
    jenkinsPassword: String,
    hookSecret: String,
) {
    private val client = JavaHttpClient()
    private fun origin(port: Int) = "http://$host:$port"

    /** The credential ARTIE's services and the watcher present at each party, by port. */
    private val credentials = mapOf(
        GITHUB_PORT to "Bearer ${GitHubService.TOKEN}",
        WATCHER_PORT to "Bearer ${OfferWatcher.TOKEN}",
        CI_PORT to "Bearer ${CiWrapper.TOKEN}",
        DEV_PORT to "Bearer ${DevEnvironment.TOKEN}",
    )

    /** A client that presents, at each party, the credential it takes. */
    private val credentialed: HttpHandler = { request ->
        val port = URI(request.uri.toString()).port
        val presented = credentials[port]?.takeIf { request.header("Authorization") == null }
        client(presented?.let { request.header("Authorization", it) } ?: request)
    }

    val github = GitHubService(repository = GitRepository(repository)).apply { reset() }

    val replies = ReplyService(
        origin = origin(REPLIES_PORT),
        realm = "ARTIE's replies",
        tokens = listOf(REPLY_TOKEN),
        result = """{"type": "object", "additionalProperties": true}""",
        ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        requestKeys = { request -> Keys(credentialed).linkedFrom(request) },
    )

    val watcher = OfferWatcher(
        reader = credentialed,
        answerer = Answerer(client, WatcherKey.signer, "Bearer $REPLY_TOKEN"),
        origin = origin(WATCHER_PORT),
        callerKeys = { href -> Keys(client).at(href) },
    )

    val ci = CiWrapper(
        jenkins = JenkinsHttp(client, JENKINS, jenkinsUser, jenkinsPassword),
        answerer = Answerer(client, CiKey.signer, "Bearer $REPLY_TOKEN"),
        hookSecret = hookSecret,
        origin = origin(CI_PORT),
        previewHost = host,
        callerKeys = { href -> Keys(client).at(href) },
    )

    val dev = DevEnvironment(commit = GitRepository(repository).head("main"))

    /**
     * ARTIE's replies, and what ARTIE does to its own replies out of band, until its runner holds them itself: open a
     * reply before asking (`POST /_release/replies/{reply}`), and keep the request it made
     * (`PUT /_release/replies/{reply}/asked`, the request's absolute address as the body).
     */
    private val repliesWithControls: HttpHandler = { request ->
        val path = request.uri.path
        when {
            request.method == Method.POST && path.startsWith(CONTROL) -> {
                replies.open(path.removePrefix(CONTROL))
                Response(Status.NO_CONTENT)
            }
            request.method == Method.PUT && path.startsWith(CONTROL) && path.endsWith("/asked") -> {
                replies.asked(path.removePrefix(CONTROL).removeSuffix("/asked"), request.bodyString().trim())
                Response(Status.NO_CONTENT)
            }
            else -> replies.handler(request)
        }
    }

    /** GitHub works out whether a pull request can be merged: `PUT /_release/github/pulls/{n}/mergeable`. */
    private val githubWithControls: HttpHandler = { request ->
        val path = request.uri.path
        if (request.method == Method.PUT && path.startsWith(PULLS) && path.endsWith("/mergeable")) {
            val number = path.removePrefix(PULLS).removeSuffix("/mergeable").toLong()
            github.mergeable(number, request.bodyString().trim().toBooleanStrictOrNull())
            Response(Status.NO_CONTENT)
        } else {
            github.handler(request)
        }
    }

    /** Serves every party, each on its own port, and looks for offers every few seconds, until the process ends. */
    fun serve() {
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(
            { runCatching { watcher.look() }.onFailure { System.err.println("watcher: $it") } },
            LOOK_EVERY, LOOK_EVERY, TimeUnit.SECONDS,
        )
        listOf(
            GITHUB_PORT to githubWithControls,
            WATCHER_PORT to watcher.handler,
            REPLIES_PORT to repliesWithControls,
            CI_PORT to ci.handler,
            DEV_PORT to dev.handler,
        ).map { (port, handler) ->
            handler.asServer(Jetty(port)).start().also { println("release: serving on ${origin(it.port())}") }
        }.last().block()
    }

    /** For a check from outside: [path] at [port], as the release's own client reads it. */
    fun read(port: Int, path: String): Response = credentialed(Request(Method.GET, origin(port) + path))

    companion object {
        const val GITHUB_PORT = 8098
        const val WATCHER_PORT = 8099
        const val REPLIES_PORT = 8160
        const val CI_PORT = 8170
        const val DEV_PORT = 8190
        const val JENKINS = "http://localhost:8171"
        const val REPLY_TOKEN = "artie-reply-token"
        private const val CONTROL = "/_release/replies/"
        private const val PULLS = "/_release/github/pulls/"
        private const val LOOK_EVERY = 3L
    }
}
