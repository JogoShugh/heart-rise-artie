package com.artie.heart.sims.ci

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Hypermedia
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.Replies
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.Signatures
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.artie.heart.sims.library.SignedRequests
import com.artie.heart.sims.library.SigningKey
import com.artie.heart.sims.rise.Answerer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.security.PublicKey
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/** The wrapper's signing key: every answer it sends on a caller's reply is signed with it. */
object CiKey : SigningKey("ci-2026-11") {
    const val KEY_ID = "ci-2026-11"
}

/** One build the wrapper was asked for: the commit, the reply it answers on, and how it stands. */
data class Build(
    val id: Long,
    val commit: String,
    val reply: String,
    val standing: String = QUEUED,
    val version: Int = 0,
    val jenkinsNumber: Int? = null,
    val previewPort: Int? = null,
) {
    companion object {
        const val QUEUED = "queued"
        const val RUNNING = "running"
        const val SUCCEEDED = "succeeded"
        const val FAILED = "failed"
    }
}

/**
 * A real Jenkins job, [job], served as a HEART service at [origin]: its entry offers `ci:build`, a request to build a
 * commit and be told how it goes on the caller's reply (RISE); each build is a resource at `/builds/{id}`. Jenkins'
 * notifications (its Notification plugin), at `/hooks/jenkins` with [hookSecret], become signed answers on the
 * caller's reply, sent by [answerer]. A succeeded build links the deployment it started on [previewHost], the smoke
 * script it archived and its log. A caller presents one of [tokens] and signs its request by a key of the set it
 * names, read by [callerKeys].
 */
class CiWrapper(
    private val jenkins: Jenkins,
    private val answerer: Answerer,
    private val hookSecret: String,
    private val origin: String = ORIGIN,
    private val previewHost: String = "localhost",
    tokens: List<String> = listOf(TOKEN),
    private val callerKeys: (String) -> Map<String, PublicKey> = { emptyMap() },
    private val job: String = "app",
) {
    private val mapper = ObjectMapper()
    private val builds = ConcurrentSkipListMap<Long, Build>()
    private val next = AtomicLong(1)

    private val type = ResourceType(
        collection = "builds",
        profile = PROFILE,
        acts = emptyList(),
        offers = Offers(keys = STANDINGS, offered = { emptyList() }, whyAbsent = { _, _ -> "" }),
        state = StateView<Build, String>(
            keyOf = { it.standing },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { linkedMapOf("id" to it.id, "commit" to it.commit, "standing" to it.standing, "jenkinsBuild" to it.jenkinsNumber) },
            hypermediaOf = { Hypermedia(links = links(it, absolute = false)) },
            keyField = "standing",
        ),
        finding = Finding(
            noun = "build",
            search = Named("searchBuilds", "search-builds"),
            lookup = Named("getBuild", "get-build"),
            idTitle = "Build number",
            idDescription = "The build's number, in its Location",
        ),
        bookmarkActs = listOf(Act("build", "build", BUILD_INPUT)),
    )

    private val resource = HeartResource(
        layers = Layers(type),
        load = { id -> builds[id] ?: throw ResourceNotFoundException("no build $id") },
        perform = { current, _, _ -> Acted(current) },
        find = { _, id -> builds.values.filter { id == null || it.id == id }.map { Found(it.id, "Build of ${it.commit}") } },
        performAtBookmark = { _, input ->
            val build = Build(next.getAndIncrement(), input.json["commit"].asText(), input.json["_links"]["hr:reply-to"]["href"].asText())
            builds[build.id] = build
            jenkins.build(job, mapOf("SHA" to build.commit, "RELEASE_BUILD" to build.id.toString()))
            Acted(build, created = "/builds/${build.id}")
        },
    )

    private val heart: HttpHandler = signedRequests().then(
        HeartHttp(
            listOf(resource),
            Credentials.parse(users = "", tokens = tokens.joinToString(",") { Tokens.sha256(it) }),
            HeartSettings(title = "CI: job $job", realm = PROFILE.name, keys = mapOf(CiKey.KEY_ID to CiKey.pair.public)),
            extra = listOf(
                "/builds/{id}/log" bind Method.GET to { request ->
                    val text = jenkinsNumber(request)?.let { jenkins.log(job, it) }
                    text?.let { Response(Status.OK).header("Content-Type", "text/plain; charset=utf-8").body(it) } ?: Response(Status.NOT_FOUND)
                },
                "/builds/{id}/artifacts/{name}" bind Method.GET to { request ->
                    val file = jenkinsNumber(request)?.let { jenkins.artifact(job, it, request.path("name").orEmpty()) }
                    file?.let { Response(Status.OK).header("Content-Type", "text/plain; charset=utf-8").header("Cache-Control", IMMUTABLE).body(String(it)) }
                        ?: Response(Status.NOT_FOUND)
                },
            ),
        ),
    )

    /** The service: Jenkins' notifications at `/hooks/jenkins`, with no credential but the secret; HEART otherwise. */
    val handler: HttpHandler = { request ->
        if (request.method == Method.POST && request.uri.path == HOOK) notified(request) else heart(request)
    }

    /** The build [id] as it is now; null when there is none. */
    fun build(id: Long): Build? = builds[id]

    private fun jenkinsNumber(request: Request): Int? = request.path("id")?.toLongOrNull()?.let { builds[it]?.jenkinsNumber }

    /** A notification from Jenkins: the build it names moves, and the caller's reply is told, signed. */
    private fun notified(request: Request): Response {
        if (request.query("secret") != hookSecret) return Response(Status.FORBIDDEN)
        System.err.println("ci: notified ${request.bodyString().take(LOGGED)}")
        val said = mapper.readTree(request.bodyString())["build"] ?: return Response(Status.BAD_REQUEST)
        val parameters = said["parameters"]
        val id = parameters?.get("RELEASE_BUILD")?.asText()?.toLongOrNull() ?: return Response(Status.OK)
        val build = builds[id] ?: return Response(Status.NOT_FOUND)
        val port = parameters["PREVIEW_PORT"]?.asText()?.toIntOrNull() ?: DEFAULT_PREVIEW_PORT
        val moved = moved(build.copy(jenkinsNumber = said["number"]?.asInt(), previewPort = port), said)
            ?: return Response(Status.OK)
        builds[id] = moved
        tell(moved, said)
        return Response(Status.OK)
    }

    /** The build after Jenkins said [said]; null when it says nothing that moves it. */
    private fun moved(build: Build, said: JsonNode): Build? {
        val standing = when (said["phase"]?.asText()) {
            "STARTED" -> Build.RUNNING
            "FINALIZED" -> if (said["status"]?.asText() == "SUCCESS") Build.SUCCEEDED else Build.FAILED
            else -> return null
        }
        return build.copy(standing = standing, version = build.version + 1)
    }

    /** The answer on the caller's reply that says how [build] stands now. */
    private fun tell(build: Build, said: JsonNode) {
        val self = "$origin/builds/${build.id}"
        val inReplyTo = mapOf(Replies.IN_REPLY_TO to mapOf("href" to self))
        when (build.standing) {
            Build.RUNNING -> answerer.answer(
                build.reply, "progress",
                mapOf("state" to "awaiting", "percent" to STARTED_PERCENT, "note" to "Jenkins build #${build.jenkinsNumber} started", "_links" to inReplyTo),
            )
            Build.SUCCEEDED -> answerer.answer(
                build.reply, "completed",
                mapOf(
                    "state" to "completed",
                    "result" to mapOf("build" to self, "commit" to build.commit, "_links" to links(build, absolute = true)),
                    "_links" to inReplyTo,
                ),
            )
            else -> answerer.answer(
                build.reply, "failed",
                mapOf(
                    "state" to "failed",
                    "type" to "$origin/problems/build-failed",
                    "title" to "The build failed",
                    "detail" to "Jenkins build #${build.jenkinsNumber} ended ${said["status"]?.asText()}",
                    "_links" to inReplyTo + (LOG to mapOf("href" to "$self/log")),
                ),
            )
        }
    }

    /** What [build] links: the caller's reply and the keys it answers with; its log; once succeeded, what it made. */
    private fun links(build: Build, absolute: Boolean): Map<String, Any> {
        val base = if (absolute) origin else ""
        val made = if (build.standing == Build.SUCCEEDED) {
            mapOf(
                DEPLOYMENT to mapOf("href" to "http://$previewHost:${build.previewPort ?: DEFAULT_PREVIEW_PORT}/"),
                SMOKE_TEST to mapOf("href" to "$base/builds/${build.id}/artifacts/$SMOKE_GOAL"),
            )
        } else {
            emptyMap()
        }
        val own = if (absolute) emptyMap() else mapOf(
            "hr:reply-to" to mapOf("href" to build.reply),
            "hr:keys" to mapOf("href" to Signatures.KEYS, "type" to Signatures.JWK_SET),
        )
        return own + made + (LOG to mapOf("href" to "$base/builds/${build.id}/log"))
    }

    /** Refuses, with 401, a request to build that its caller did not sign with a key of the set it names. */
    private fun signedRequests(): Filter = Filter { next ->
        { request ->
            if (request.method != Method.POST || request.uri.path != BUILD) {
                next(request)
            } else {
                val keys = runCatching {
                    callerKeys(mapper.readTree(request.bodyString())["_links"]["hr:keys"]["href"].asText())
                }.getOrDefault(emptyMap())
                val key = SignedRequests.keyIdOf(request)?.let { keys[it] }
                if (key != null && SignedRequests.signedBy(request, origin, key)) {
                    next(request)
                } else {
                    SignedRequests.unauthorized(request, "CI", keys.keys.firstOrNull() ?: "")
                }
            }
        }
    }

    companion object {
        const val ORIGIN = "http://localhost:8170"
        const val TOKEN = "ci-token"
        const val HOOK = "/hooks/jenkins"
        const val BUILD = "/actions/build"
        const val DEPLOYMENT = "ci:deployment"
        const val SMOKE_TEST = "ci:smoke-test"
        const val LOG = "ci:log"
        const val SMOKE_GOAL = "smoke.goal.md"
        private const val DEFAULT_PREVIEW_PORT = 8180
        private const val LOGGED = 600
        private const val STARTED_PERCENT = 10
        private const val IMMUTABLE = "public, max-age=31536000, immutable"
        private val STANDINGS = listOf(Build.QUEUED, Build.RUNNING, Build.SUCCEEDED, Build.FAILED)

        /** The result a caller's reply takes from a build: the build and its commit, and what it made, as links. */
        const val RESULT = """{"type": "object", "required": ["build", "commit"], "properties": {""" +
            """"build": {"type": "string", "format": "uri"}, "commit": {"type": "string"}}, "additionalProperties": true}"""

        private const val BUILD_INPUT = """{
  "type": "object",
  "required": ["commit", "_links"],
  "properties": {
    "commit": {"type": "string", "minLength": 7, "title": "Commit", "description": "The commit to build"},
    "_links": {
      "type": "object",
      "required": ["hr:reply-to", "hr:keys"],
      "additionalProperties": true,
      "properties": {
        "hr:reply-to": {"type": "object", "required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}}},
        "hr:keys": {"type": "object", "required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}}}
      }
    }
  }
}"""

        val PROFILE = Profile(
            name = "ci",
            title = "CI",
            doc = "A Jenkins job, built at a commit on request, told on the caller's reply as it goes.",
            relations = listOf(
                Relation("build", AlpsType.UNSAFE, "Build a commit", "Builds the commit and deploys it as a preview, telling the caller's reply how it goes.", "Offered at the entry."),
                Relation("deployment", AlpsType.SAFE, "Deployment", "The deployment a build started.", "Linked once the build has succeeded."),
                Relation("smoke-test", AlpsType.SAFE, "Smoke test", "The kept smoke script the build archived, for its commit.", "Linked once the build has succeeded."),
                Relation("log", AlpsType.SAFE, "Log", "The build's console log, as Jenkins keeps it.", "Linked from every build."),
                Relation("search-builds", AlpsType.SAFE, "Find builds", "Lists the builds the wrapper was asked for.", "Always."),
                Relation("get-build", AlpsType.SAFE, "Get a build", "One build, by its number.", "Always."),
            ),
        )
    }
}
