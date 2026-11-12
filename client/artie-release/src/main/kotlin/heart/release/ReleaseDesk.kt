package heart.release

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
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import heart.Hosted
import heart.HostedRun
import heart.script.Replies
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import java.security.PublicKey
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** A release: the pull request it releases, how it stands, and what its run noted, or where it stopped. */
data class Release(
    val id: Long,
    val pull: Long,
    /** The release's other inputs, as the person asking gave them: what its script fills its slots with. */
    val inputs: Map<String, String> = emptyMap(),
    val state: String = ReleaseDesk.RUNNING,
    val notes: List<String> = emptyList(),
    val stopped: String? = null,
    val version: Int = 0,
)

/**
 * The release desk (release-desk.feature), a HEART service: its entry offers `desk:release`, a pull
 * request to release. Each release is a resource, `/releases/{n}`, running until the desk's run of its kept
 * [script], with ARTIE made by [artie], ends or stops. ARTIE asks with the desk's [replies], served here too, at
 * `/replies/…`, with the keys the desk signs with at `/keys`; each run's trace is at `/releases/{n}/trace`.
 */
class ReleaseDesk(
    private val script: () -> Map<*, *>,
    private val replies: DeskReplies,
    private val artie: (Replies) -> Hosted,
    tokens: List<String> = listOf(TOKEN),
    keys: Map<String, PublicKey> = emptyMap(),
) {
    private val releases = ConcurrentSkipListMap<Long, Release>()
    private val runs = ConcurrentSkipListMap<Long, HostedRun>()
    private val next = AtomicLong(1)

    private val type = ResourceType(
        collection = "releases",
        profile = PROFILE,
        acts = emptyList(),
        offers = Offers(keys = STATES, offered = { emptyList() }, whyAbsent = { _, _ -> "" }),
        state = StateView<Release, String>(
            keyOf = { it.state },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = {
                linkedMapOf(
                    "id" to it.id, "pull" to it.pull, "inputs" to it.inputs, "state" to it.state, "notes" to it.notes,
                )
                    .also { body -> it.stopped?.let { why -> body["stopped"] = why } }
            },
            hypermediaOf = { release ->
                Hypermedia(links = mapOf("desk:trace" to mapOf("href" to "/releases/${release.id}/trace")))
            },
            keyField = "state",
        ),
        finding = Finding(
            noun = "release",
            search = Named("searchReleases", "search-releases"),
            lookup = Named("getRelease", "get-release"),
            idTitle = "Release number",
            idDescription = "The release's number, in its Location",
        ),
        bookmarkActs = listOf(Act("release", "release", RELEASE_INPUT)),
    )

    private val resource = HeartResource(
        layers = Layers(type),
        load = { id -> releases[id] ?: throw ResourceNotFoundException("no release $id") },
        perform = { current, _, _ -> Acted(current) },
        find = { _, id -> releases.values.filter { id == null || it.id == id }.map { Found(it.id, "#${it.pull}") } },
        performAtBookmark = { _, input ->
            val given = input.json.properties().filter { (name, _) -> name != "pull" }
                .associate { (name, value) -> name to value.asText() }
            val release = Release(next.getAndIncrement(), input.json["pull"].asLong(), given)
            releases[release.id] = release
            start(release)
            Acted(release, created = "/releases/${release.id}")
        },
    )

    private val heart: HttpHandler = HeartHttp(
        listOf(resource),
        Credentials.parse(users = "", tokens = tokens.joinToString(",") { Tokens.sha256(it) }),
        HeartSettings(title = "Release desk", realm = PROFILE.name, keys = keys),
    )

    /** The desk, its replies, and each run's trace, at one origin. */
    val handler: HttpHandler = { request ->
        val path = request.uri.path
        when {
            path.startsWith("/replies/") || path == "/keys" -> replies.handler(request)
            path.matches(TRACE) -> trace(path.removePrefix("/releases/").removeSuffix("/trace").toLong())
            else -> heart(request)
        }
    }

    /** The release [id] as it stands now. */
    fun release(id: Long): Release? = releases[id]

    /** The run of the release [id], once it is over. */
    fun run(id: Long): HostedRun? = runs[id]

    private fun start(release: Release) = thread(name = "release-${release.id}") {
        val run = artie(replies).run(script(), release.inputs + ("pull" to "${release.pull}"))
        runs[release.id] = run
        releases[release.id] = release.copy(
            state = if (run.stopped == null) ENDED else STOPPED,
            notes = run.run.notes.toList(),
            stopped = run.stopped?.message,
            version = release.version + 1,
        )
    }

    private fun trace(id: Long): Response = runs[id]
        ?.let { Response(Status.OK).header("Content-Type", "application/yaml").body(it.run.traceYaml()) }
        ?: Response(Status.NOT_FOUND)

    companion object {
        const val TOKEN = "desk-token"
        const val RUNNING = "running"
        const val ENDED = "ended"
        const val STOPPED = "stopped"
        private val STATES = listOf(RUNNING, ENDED, STOPPED)
        private val TRACE = Regex("^/releases/\\d+/trace$")

        private const val RELEASE_INPUT = """{
  "type": "object",
  "required": ["pull"],
  "properties": {
    "pull": {"type": "integer", "minimum": 1, "title": "Pull request", "description": "The pull request to release"},
    "book": {"type": "string", "title": "Book", "description": "The book the smoke test finds and reads"},
    "author": {"type": "string", "title": "Author", "description": "Who wrote it, as the smoke test checks"},
    "problem": {
      "type": "string", "enum": ["damaged", "missing-pages", "wrong-book", "lost"],
      "title": "Problem", "description": "The kind of problem the smoke test reports"
    },
    "comment": {"type": "string", "title": "Comment", "description": "What the smoke test says of the problem"}
  },
  "additionalProperties": false
}"""

        val PROFILE = Profile(
            name = "desk",
            title = "Release desk",
            doc = "Releases pull requests, by a kept script ARTIE runs.",
            relations = listOf(
                Relation(
                    "release", AlpsType.UNSAFE, "Release a pull request",
                    "Starts a release: watched, merged, built, smoke tested, promoted or stopped.",
                    "Offered at the entry.",
                ),
                Relation("search-releases", AlpsType.SAFE, "Find releases", "Lists the releases.", "Always."),
                Relation("get-release", AlpsType.SAFE, "Get a release", "One release, by its number.", "Always."),
                Relation(
                    "trace", AlpsType.SAFE, "Trace", "The release run's trace, step by step, once it is over.",
                    "Once the run is over.",
                ),
            ),
        )
    }
}
