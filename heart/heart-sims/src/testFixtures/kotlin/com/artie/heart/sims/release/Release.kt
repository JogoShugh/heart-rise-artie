package com.artie.heart.sims.release

import com.artie.heart.sims.ci.CiKey
import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.ci.JenkinsHttp
import com.artie.heart.sims.rise.Answerer
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.Keys
import com.artie.heart.sims.rise.ReplyService
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.Jetty
import org.http4k.server.asServer

/**
 * The merge watch release's parties, each a process-wide server at its own origin on [host]: ARTIE's replies on
 * 8160, and the CI wrapper on 8170 in front of the real Jenkins on 8171 (exhibits/merge-watch-release/jenkins).
 */
class Release(
    private val host: String,
    jenkinsUser: String,
    jenkinsPassword: String,
    hookSecret: String,
) {
    private val client = JavaHttpClient()
    private val replyOrigin = "http://$host:$REPLIES_PORT"
    private val ciOrigin = "http://$host:$CI_PORT"

    val replies = ReplyService(
        origin = replyOrigin,
        realm = "ARTIE's replies",
        tokens = listOf(REPLY_TOKEN),
        result = """{"type": "object", "additionalProperties": true}""",
        ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        requestKeys = { request -> Keys(client, "Bearer ${CiWrapper.TOKEN}").linkedFrom(request) },
    )

    val ci = CiWrapper(
        jenkins = JenkinsHttp(client, JENKINS, jenkinsUser, jenkinsPassword),
        answerer = Answerer(client, CiKey.signer, "Bearer $REPLY_TOKEN"),
        hookSecret = hookSecret,
        origin = ciOrigin,
        previewHost = host,
        callerKeys = { href -> Keys(client).at(href) },
    )

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

    /** Serves every party, each on its own port, until the process ends. */
    fun serve() {
        listOf(REPLIES_PORT to repliesWithControls, CI_PORT to ci.handler).map { (port, handler) ->
            handler.asServer(Jetty(port)).start().also { println("release: serving on http://$host:${it.port()}") }
        }.last().block()
    }

    companion object {
        const val REPLIES_PORT = 8160
        const val CI_PORT = 8170
        const val JENKINS = "http://localhost:8171"
        const val REPLY_TOKEN = "artie-reply-token"
        private const val CONTROL = "/_release/replies/"
    }
}
