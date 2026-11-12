package steps

import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.OfferWatcher
import com.artie.heart.sims.rise.ReplyService
import com.artie.heart.sims.rise.WatcherKey
import heart.Credential
import heart.CredentialSource
import heart.Hosted
import heart.HostedRun
import heart.Origin
import heart.SigningKey
import heart.release.ArtieAnswers
import heart.release.DeskReplies
import heart.release.ReleaseDesk
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import java.net.URI
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Steps for a goal that crosses parties and waits on their answers (goal-rise.feature): the desk's replies (8160) and
 * the offer watch (8099) in this process, the watch answering through ARTIE and looking every tenth of a second; the
 * scenario's client signs as the desk, and asks with the desk's replies.
 */
class GoalRiseSteps(
    private val ctx: ClientContext,
    private val replay: ReplaySteps,
    private val goals: GoalSteps,
) {
    private lateinit var router: InProcessTransport
    private var looking = true
    private var hosted: HostedRun? = null

    @Given("the offer watch and the desk's replies run in this process, the watch answering through ARTIE")
    fun parties() {
        val service = ReplyService(
            origin = DESK, realm = "The desk's replies", tokens = listOf(REPLY_TOKEN),
            result = """{"type": "object", "additionalProperties": true}""",
            ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
            requestKeys = { mapOf(WatcherKey.KEY_ID to WatcherKey.pair.public) },
        )
        val replies = DeskReplies(service, DESK)
        val tokens = listOf(ReleaseDesk.TOKEN, REPLY_TOKEN)
        val desk = ReleaseDesk({ emptyMap<String, Any>() }, replies, { Hosted() }, tokens)
        lateinit var watcher: OfferWatcher
        val routes: HttpHandler = { request: Request ->
            when (URI(request.uri.toString()).port) {
                WATCHER_PORT -> watcher.handler(request)
                else -> desk.handler(request)
            }
        }
        router = InProcessTransport(routes)
        val reader: HttpHandler = { request -> routes(request.header("Authorization", "Bearer $REPLY_TOKEN")) }
        val key = SigningKey(WatcherKey.KEY_ID, WatcherKey.pair.private)
        watcher = OfferWatcher(
            reader = reader,
            answerer = ArtieAnswers("watch", key, REPLY_TOKEN, wire = router),
            origin = WATCHER,
            callerKeys = { mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public) },
        )
        thread(isDaemon = true) {
            while (looking) {
                runCatching { watcher.look() }
                Thread.sleep(LOOK_MILLIS)
            }
        }
        ctx.inProcess = router
        ctx.credentials = credentials
        ctx.signing = SigningKey(ArtieKey.KEY_ID, ArtieKey.pair.private)
        ctx.replies = replies
    }

    @After
    fun stopLooking() {
        looking = false
    }

    @When("the goal {string} runs with ARTIE in the desk, captured in {string}, given")
    fun goalRunsInDesk(file: String, capture: String, table: DataTable) =
        goals.goalRuns(file, capture, table)

    @Then("the watch was asked with the desk's reply, signed by the desk")
    fun askedWithReply() {
        val asked = checkNotNull(router.sent.firstOrNull { it.method == "POST" && it.url.endsWith("/actions/watch") }) {
            "sent: " + router.sent.joinToString { "${it.method} ${it.url}" }
        }
        assertTrue(asked.body.orEmpty().contains("\"hr:reply-to\":{\"href\":\"$DESK/replies/"), "body: ${asked.body}")
        assertTrue(asked.header("Signature-Input").orEmpty().contains("keyid=\"${ArtieKey.KEY_ID}\""), "$asked")
    }

    @When("{string} runs with ARTIE in the desk, given")
    fun scriptRunsInDesk(script: String, table: DataTable) {
        val given = table.asMaps().associate { it.getValue("name") to it.getValue("value") }
        val text = Files.readString(replay.folder.resolve(script))
        val data = org.yaml.snakeyaml.Yaml().load<Map<*, *>>(text)
        hosted = Hosted(credentials, ctx.signing, router, "desk", replies = ctx.replies).run(data, given)
    }

    @Then("the replay finished, having followed {string} to {string}, with no model")
    fun replayFinished(relation: String, to: String) {
        val run = checkNotNull(hosted)
        assertNull(run.stopped, "stopped: ${run.stopped?.message}")
        val followed = run.run.trace.firstOrNull { it.asked.params["relation"] == relation }
        val ran = run.run.trace.joinToString("\n") {
            "${it.asked.say} ${it.asked.params["relation"] ?: ""} ${it.outcome} ${it.address}"
        }
        assertEquals(to, followed?.address, ran + "\n\n" + Files.readString(replay.folder.resolve("watch.heart.yaml")))
        // Only a reasoned step calls a model: a replay has none.
        assertTrue(run.run.trace.none { it.asked.mode == "intent" }, "a step was reasoned")
    }

    private val credentials = object : CredentialSource {
        override fun credentialFor(origin: Origin): Credential? = when (origin.port) {
            WATCHER_PORT -> Credential.Bearer(OfferWatcher.TOKEN)
            DESK_PORT -> Credential.Bearer(REPLY_TOKEN)
            else -> null
        }
    }

    private companion object {
        const val DESK_PORT = 8160
        const val WATCHER_PORT = 8099
        const val DESK = "http://localhost:$DESK_PORT"
        const val WATCHER = "http://localhost:$WATCHER_PORT"
        const val REPLY_TOKEN = "artie-reply-token"
        const val LOOK_MILLIS = 100L
    }
}
