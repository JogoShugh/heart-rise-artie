package steps

import com.artie.heart.Signatures
import com.artie.heart.Signed
import heart.Credential
import heart.CredentialSource
import heart.HeartRequest
import heart.HeartResponse
import heart.Hosted
import heart.HostedRun
import heart.Origin
import heart.SigningKey
import heart.Transport
import heart.script.Replies
import heart.script.Reply
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Steps for ARTIE hosted in a service (hosted.feature): each host runs ARTIE in this process, on the scenario's
 * scripted wire, with only the credentials and key the host gives it.
 */
class HostedSteps(private val ctx: ClientContext) {
    private val tokens = mutableMapOf<String, Pair<String, String>>()
    private val keys = mutableMapOf<String, SigningKey>()
    private val masks = mutableMapOf<String, List<String>>()
    private val replies = mutableMapOf<String, Replies>()
    private val asked = mutableListOf<Pair<String, String>>()
    private val publicKeys = mutableMapOf<String, PublicKey>()
    private val runs = ConcurrentHashMap<String, HostedRun>()
    private val held = mutableMapOf<String, CountDownLatch>()
    private val waitingOn = mutableMapOf<String, String>()

    /** What the wire answered each GET, so a second reader of the same resource reads it too. */
    private val served = ConcurrentHashMap<String, HeartResponse>()

    /** Every request that reached the wire, from any host. */
    private val onTheWire = java.util.concurrent.CopyOnWriteArrayList<HeartRequest>()

    /** The scripted service, safe for hosts on several threads: a resource read once is read again alike. */
    private val shared = object : Transport {
        override fun send(request: HeartRequest): HeartResponse = synchronized(this) {
            onTheWire += request
            if (request.method == "GET") served[request.url]?.let { return it }
            ctx.transport.send(request).also { if (request.method == "GET") served[request.url] = it }
        }
    }

    @Given("the host {string} gives ARTIE the token {string} for {string}")
    fun token(host: String, token: String, origin: String) {
        tokens[host] = origin to token
    }

    @Given("the host {string} keeps replies, opening {string} with its keys at {string}")
    fun keepsReplies(host: String, address: String, keys: String) {
        replies[host] = object : Replies {
            override fun open() = Reply(address, keys)

            override fun asked(reply: Reply, request: String) {
                asked += reply.address to request
            }

            override fun await(reply: Reply, atMost: kotlin.time.Duration) = false
        }
    }

    @Then("the run of the host {string} kept these exchanges:")
    fun keptExchanges(host: String, table: DataTable) {
        val kept = ended(host).run.trace.flatMap { step ->
            val said = listOfNotNull(step.asked.say.substringBefore(' '), step.asked.params["relation"])
                .joinToString(" ")
            step.exchanges.map { exchange ->
                mapOf(
                    "step" to said, "method" to exchange.sent.method, "url" to exchange.sent.url,
                    "status" to "${exchange.received.status}",
                    "Authorization" to exchange.sent.headers.entries
                        .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value.orEmpty(),
                )
            }
        }
        assertEquals(table.asMaps(), kept)
    }

    @Then("the acts on the wire carried:")
    fun actsCarried(table: DataTable) {
        val acts = ctx.transport.requests.filter { it.method != "GET" }
            .map { mapOf("method" to it.method, "url" to it.url, "If-Match" to it.header("If-Match").orEmpty()) }
        assertEquals(table.asMaps(), acts)
    }

    @Then("the host {string} has its run back, ended")
    fun ranToEnd(host: String) {
        ended(host)
    }

    @Then("the POST to {string} carried the body:")
    fun carried(url: String, body: String) {
        val post = ctx.transport.requests.single { it.method == "POST" && it.url == url }
        assertEquals(body.trim(), post.body)
    }

    @Then("the host {string} was told the reply {string} was asked at {string}")
    fun toldAsked(host: String, reply: String, request: String) {
        assertTrue(replies.containsKey(host))
        assertEquals(listOf(reply to request), asked)
    }

    @Then("the run of the host {string} noted {string}")
    fun noted(host: String, note: String) {
        assertTrue(note in ended(host).run.notes, "notes: ${ended(host).run.notes}")
    }

    @Given("the host {string} gives ARTIE a reasoner that will choose {string} with {}")
    fun reasoner(host: String, form: String, fields: String) {
        val chosen = kotlinx.serialization.json.Json.parseToJsonElement(fields) as kotlinx.serialization.json.JsonObject
        reasoners[host] = ScriptedReasoner(heart.Selection.Act(form, chosen))
    }

    private val reasoners = mutableMapOf<String, heart.Reasoner>()
    private val rungs = mutableMapOf<String, heart.Rung>()
    private val caches = mutableMapOf<String, heart.HttpCache>()

    @Given("the host {string} gives each run its own cache")
    fun ownCaches(host: String) {
        caches.remove(host)
    }

    @Given("the host {string} keeps one cache across its runs")
    fun oneCache(host: String) {
        caches[host] = heart.HttpCache()
    }

    @Then("the wire carried {int} GET to {string}")
    fun getsTo(count: Int, url: String) {
        val at = ctx.expand(url)
        assertEquals(count, onTheWire.count { it.method == "GET" && it.url == at }, "GETs to $at")
    }

    @Given("the host {string} has ARTIE read at rung {string}")
    fun readsAt(host: String, rung: String) {
        rungs[host] = heart.Rung.valueOf(rung)
    }

    @Then("the GET to {string} carried header {string} set to {string}")
    fun getHeader(url: String, name: String, value: String) {
        val sent = ctx.transport.requests.filter { it.method == "GET" && it.url == url }.map { it.header(name) }
        assertTrue(sent.isNotEmpty() && sent.all { it == value }, "$name on the GETs to $url: $sent")
    }

    @Given("the host {string} lets ARTIE send credentials over plain HTTP to {string}")
    fun plainHttp(host: String, mask: String) {
        masks[host] = listOf(mask)
    }

    @Then("what reached the wire for {string} was {string}")
    fun reached(url: String, sent: String) {
        val seen = ctx.transport.requests.filter { it.url == url }
            .map { "${it.method}, with ${it.header("Authorization") ?: "no credential"}" }
        assertEquals(sent, seen.singleOrNull() ?: if (seen.isEmpty()) "nothing" else seen.toString())
    }

    @Given("the host {string} gives ARTIE its signing key {string}")
    fun signingKey(host: String, keyId: String) {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        keys[host] = SigningKey(keyId, pair.private)
        publicKeys[keyId] = pair.public
    }

    @Given("the host {string} reads its entry only once the host {string} has its run back")
    fun readsAfter(host: String, other: String) {
        waitingOn[host] = other
        held[other] = CountDownLatch(1)
    }

    @When("the host {string} runs this kept script with ARTIE:")
    fun runs(host: String, script: String) {
        runs[host] = hosted(host).run(yaml(script))
    }

    @When("the hosts run, at the same time, each with ARTIE:")
    fun runTogether(table: DataTable) {
        val threads = table.asMaps().map { row ->
            val host = row.getValue("host")
            thread(name = host) {
                runs[host] = hosted(host).run(yaml(row.getValue("script")))
                held[host]?.countDown()
            }
        }
        threads.forEach { it.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)) }
    }

    @Then("the host {string} has its run back, ended, with steps:")
    fun ranSteps(host: String, table: DataTable) {
        val run = ended(host)
        val steps = run.run.trace.map { step ->
            val said = listOfNotNull(step.asked.say.substringBefore(' '), step.asked.params["relation"])
            mapOf("step" to said.joinToString(" "), "outcome" to step.outcome)
        }
        assertEquals(table.asMaps(), steps)
    }

    @Then("every request on the wire had header {string} set to {string}")
    fun everyHeader(name: String, value: String) {
        val sent = ctx.transport.requests.map { it.header(name) }
        assertTrue(sent.isNotEmpty() && sent.all { it == value }, "$name on the wire: $sent")
    }

    @Then("the requests on the wire were signed:")
    fun signed(table: DataTable) {
        val seen = ctx.transport.requests.map { request ->
            mapOf("method" to request.method, "url" to request.url, "signed by" to (verifiedBy(request) ?: ""))
        }
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value ?: "" } }, seen)
    }

    @Then("the signature on the POST covers, in order:")
    fun covers(table: DataTable) {
        val post = ctx.transport.requests.single { it.method == "POST" }
        val input = post.header("Signature-Input").orEmpty()
        val covered = Regex("\"([^\"]+)\"").findAll(input.substringAfter('(').substringBefore(')'))
            .map { it.groupValues[1] }.toList()
        assertEquals(table.asList().drop(1), covered)
    }

    @Then("each host has its run back, ended, with only its own:")
    fun ownRuns(table: DataTable) {
        table.asMaps().forEach { row ->
            val run = ended(row.getValue("host")).run
            assertEquals(row.getValue("steps").toInt(), run.trace.size, "steps of ${row["host"]}")
            assertEquals(listOfNotNull(row["notes"]), run.notes, "notes of ${row["host"]}")
        }
    }

    private fun ended(host: String): HostedRun {
        val run = checkNotNull(runs[host]) { "the host $host has no run back" }
        assertNull(run.stopped, "the run of $host stopped: ${run.stopped?.message}")
        return run
    }

    /** The key id whose public key verifies [request]'s signature, if it carries one; a forgery fails here. */
    private fun verifiedBy(request: HeartRequest): String? {
        val input = request.header("Signature-Input") ?: return null
        val params = input.removePrefix("sig1=")
        val keyId = Regex("keyid=\"([^\"]+)\"").find(params)?.groupValues?.get(1)
        val signed = Signed(
            method = request.method,
            target = request.url,
            contentDigest = request.header("Content-Digest").orEmpty(),
            ifMatch = request.header("If-Match").orEmpty(),
            params = params,
            signature = request.header("Signature").orEmpty().removePrefix("sig1=:").removeSuffix(":"),
        )
        val key = checkNotNull(publicKeys[keyId]) { "signed by an unknown key: $keyId" }
        assertTrue(Signatures.verify(signed, request.body.orEmpty(), key), "the signature by $keyId does not verify")
        return keyId
    }

    private fun hosted(host: String): Hosted {
        val credentials = object : CredentialSource {
            override fun credentialFor(origin: Origin): Credential? = tokens[host]
                ?.takeIf { (at, _) -> Origin.of(at) == origin }?.let { (_, token) -> Credential.Bearer(token) }
        }
        val wire = waitingOn[host]?.let { other -> heldUntil(other) } ?: shared
        return Hosted(credentials, keys[host], wire, host, masks[host].orEmpty(), replies[host])
            .also { it.reasoner = reasoners[host] }
            .also { it.rung = rungs[host] ?: heart.Rung.L0 }
            .also { it.cache = caches[host] }
    }

    /** The scripted service, which answers only once [other] has its run back. */
    private fun heldUntil(other: String) = object : Transport {
        override fun send(request: HeartRequest): HeartResponse {
            check(held.getValue(other).await(WAIT_SECONDS, TimeUnit.SECONDS)) { "$other never had its run back" }
            return shared.send(request)
        }
    }

    private fun yaml(text: String): Map<*, *> =
        com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
            .readValue(text, Map::class.java)

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
