package steps

import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.ReplyService
import com.artie.heart.sims.rise.WatcherKey
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.Credential
import heart.CredentialSource
import heart.Hosted
import heart.Origin
import heart.SigningKey
import heart.release.ArtieAnswers
import heart.release.DeskReplies
import heart.release.ReleaseDesk
import heart.script.Reply
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Steps for the release desk (release-desk.feature): the desk's replies, served by heart-core in this process, a
 * party answering them through ARTIE, and the desk running a kept script with ARTIE, all on in-process wires.
 */
class ReleaseDeskSteps {
    private val yaml = ObjectMapper(YAMLFactory())
    private val json = ObjectMapper()
    private val service = ReplyService(
        origin = DESK,
        realm = "The desk's replies",
        tokens = listOf(REPLY_TOKEN),
        result = """{"type": "object", "additionalProperties": true}""",
        ownKeys = mapOf(ArtieKey.KEY_ID to ArtieKey.pair.public),
        requestKeys = { mapOf(WatcherKey.KEY_ID to WatcherKey.pair.public) },
    )
    private val replies = DeskReplies(service, DESK)
    private val toReplies = InProcessTransport(replies.handler)
    private lateinit var reply: Reply
    private var script: Map<*, *> = emptyMap<String, Any>()
    private var desk: ReleaseDesk? = null
    private var answered: Response? = null
    private var waited: Thread? = null
    private var woke: Pair<Boolean, Long>? = null
    private var askedAt = 0L

    @Given("the desk keeps a reply, asked for at the watch {string}")
    fun keepsReply(watch: String) {
        reply = replies.open()
        replies.asked(reply, watch)
    }

    @When("the watch answers the reply through ARTIE with {string}:")
    fun watchAnswers(relation: String, body: String) {
        val key = SigningKey(WatcherKey.KEY_ID, WatcherKey.pair.private)
        val fields = yaml.readValue(body, Map::class.java).mapKeys { it.key.toString() }
        askedAt = System.currentTimeMillis()
        answered = ArtieAnswers("watch", key, REPLY_TOKEN, wire = toReplies).answer(reply.address, relation, fields)
    }

    @Then("the reply reads:")
    fun replyReads(table: DataTable) {
        val read = toReplies.send(heart.HeartRequest("GET", reply.address, mapOf(AUTH to "Bearer $REPLY_TOKEN")))
        assertMembers(json.readTree(read.body), table)
    }

    @Then("the answer was signed by the key {string}")
    fun signedBy(keyId: String) {
        val patch = toReplies.sent.single { it.method == "PATCH" }
        assertTrue(patch.header("Signature-Input").orEmpty().contains("keyid=\"$keyId\""), "answered: $answered")
    }

    @Given("a run waits on the reply for up to {int} seconds")
    fun runWaits(seconds: Int) {
        waited = thread { woke = replies.await(reply, seconds.seconds) to System.currentTimeMillis() }
        Thread.sleep(SETTLE_MILLIS)
    }

    @Then("the run woke within {int} seconds, told the reply changed")
    fun runWoke(seconds: Int) {
        waited?.join(seconds * MILLIS)
        val (changed, at) = checkNotNull(woke) { "the run is still waiting" }
        assertTrue(changed && at - askedAt <= seconds * MILLIS, "changed: $changed, after ${at - askedAt} ms")
    }

    @Given("the desk's release script is:")
    fun releaseScript(text: String) {
        script = yaml.readValue(text, Map::class.java)
    }

    @When("a person asks the desk to release pull request {int}")
    fun asksToRelease(pull: Int) {
        lateinit var made: ReleaseDesk
        val artie = { kept: heart.script.Replies ->
            val credentials = object : CredentialSource {
                override fun credentialFor(origin: Origin) = Credential.Bearer(ReleaseDesk.TOKEN)
                    .takeIf { origin == Origin.of(DESK) }
            }
            Hosted(credentials, wire = InProcessTransport(made.handler), name = "desk", replies = kept)
        }
        made = ReleaseDesk({ script }, replies, artie)
        desk = made
        val entry = made.handler(Request(Method.GET, "$DESK/").header(AUTH, "Bearer ${ReleaseDesk.TOKEN}"))
        answered = made.handler(
            Request(Method.POST, "$DESK/actions/release").header(AUTH, "Bearer ${ReleaseDesk.TOKEN}")
                .header("Content-Type", "application/json").header("If-Match", entry.header("ETag").orEmpty())
                .body("""{"pull": $pull}"""),
        )
    }

    @Then("the desk answered {int}, at {string}")
    fun deskAnswered(status: Int, location: String) {
        val answer = checkNotNull(answered)
        assertEquals(status, answer.status.code, answer.bodyString())
        assertTrue(answer.header("Location").orEmpty().endsWith(location), "Location: ${answer.header("Location")}")
    }

    @Then("{string} reads, once the run is over:")
    fun readsOnceOver(path: String, table: DataTable) {
        val made = checkNotNull(desk)
        val id = path.substringAfterLast('/').toLong()
        val end = System.currentTimeMillis() + RUN_MILLIS
        while (made.release(id)?.state == ReleaseDesk.RUNNING && System.currentTimeMillis() < end) {
            Thread.sleep(SETTLE_MILLIS)
        }
        val read = made.handler(
            Request(Method.GET, DESK + path).header(AUTH, "Bearer ${ReleaseDesk.TOKEN}")
                .header("Accept", "application/hal+json"),
        )
        assertMembers(json.readTree(read.bodyString()), table)
    }

    private fun assertMembers(read: JsonNode, table: DataTable) {
        val expected = table.asMaps().associate { it.getValue("member") to it.getValue("value") }
        val seen = expected.keys.associateWith { pointer -> read.at(pointer).asText() }
        assertEquals(expected, seen, "read: $read")
    }

    private companion object {
        const val DESK = "http://localhost:8160"
        const val REPLY_TOKEN = "artie-reply-token"
        const val AUTH = "Authorization"
        const val SETTLE_MILLIS = 100L
        const val MILLIS = 1000L
        const val RUN_MILLIS = 10_000L
    }
}
