package conformance

import heart.ArtieClient
import heart.CachingTransport
import heart.HeartRepresentation
import heart.HeartRequest
import heart.HeartResponse
import heart.IllegalTransition
import heart.IntegrityCheck
import heart.IntegrityException
import heart.InvalidPayload
import heart.NotAChoice
import heart.NotAuthenticated
import heart.RuleRefused
import heart.Rung
import heart.StaleAct
import heart.Transport
import heart.ValidatorRequired
import io.cucumber.datatable.DataTable
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Steps for implementations/conformance/features, driving ARTIE through its
 * public client. heart-ts has its own steps file with the same phrases. A
 * refusal's wording is each client's own, so only its kind is asserted here.
 */
class ClientSteps {
    private class Actor(val client: ArtieClient, val log: MutableList<HeartRequest>)

    private lateinit var scenarioName: String
    private lateinit var service: Transport
    private var bookmark = BOOKMARK
    private var me: Actor? = null
    private var other: Actor? = null
    private var rep: HeartRepresentation? = null
    private var error: Exception? = null
    private var validatorBefore: String? = null
    private var sentBefore = 0

    private val actor get() = checkNotNull(me) { "no client yet" }
    private val held get() = checkNotNull(rep) { "nothing held" }

    @Before
    fun name(scenario: Scenario) {
        scenarioName = scenario.name
    }

    private fun client(rung: String, token: String?): Actor {
        val log = mutableListOf<HeartRequest>()
        val wire = object : Transport {
            override fun send(request: HeartRequest): HeartResponse {
                log += request
                val sent = token?.let { request.copy(headers = request.headers + ("Authorization" to it)) }
                return service.send(sent ?: request)
            }
        }
        return Actor(ArtieClient(CachingTransport(IntegrityCheck(wire)), Rung.valueOf(rung)), log)
    }

    @Given("the garden is at its seed state")
    fun seed() {
        service = GardenReplay.forScenario(scenarioName)
    }

    @Given("a client at rung {word} with the garden's credential")
    fun credentialed(rung: String) {
        me = client(rung, CREDENTIAL)
    }

    @Given("another client at rung {word} with the garden's credential")
    fun anotherCredentialed(rung: String) {
        other = client(rung, CREDENTIAL)
    }

    @Given("a client at rung {word} with no credential")
    fun anonymous(rung: String) {
        me = client(rung, null)
    }

    private fun trying(step: () -> HeartRepresentation) {
        try {
            rep = step()
        } catch (refused: RuntimeException) {
            error = refused
            rep = actor.client.current
        }
    }

    private fun fields(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    @When("it enters at the bookmark")
    fun enter() {
        rep = actor.client.enter(bookmark)
    }

    @When("it tries to enter at the bookmark")
    fun tryEnter() = trying { actor.client.enter(bookmark) }

    @When("it submits {string} with {}")
    fun submit(form: String, body: String) {
        validatorBefore = actor.client.current?.etag
        rep = actor.client.act(form, fields(body))
    }

    @When("it tries to submit {string} with {}")
    fun trySubmit(form: String, body: String) {
        sentBefore = actor.log.size
        trying { actor.client.act(form, fields(body)) }
    }

    @When("the other client waters bed {int} with {int} ml")
    fun otherWaters(bed: Int, ml: Int) {
        val client = checkNotNull(other).client
        client.enter(bookmark)
        client.act("garden:get-bed", fields("""{"id": $bed}"""))
        client.act("garden:water-cell", fields("""{"milliliters": $ml}"""))
    }

    @When("it follows the {string} link titled {string}")
    fun follow(rel: String, title: String) {
        rep = actor.client.follow(rel, title)
    }

    @Then("the state's {string} is {string}")
    fun stateIs(member: String, value: String) {
        assertEquals(value, held.state[member]?.jsonPrimitive?.content)
    }

    @Then("the state's {string} is {int}")
    fun stateIsNumber(member: String, value: Int) {
        assertEquals(value.toString(), held.state[member]?.jsonPrimitive?.content)
    }

    @Then("the representation is at rung {word}")
    fun rungIs(rung: String) {
        assertEquals(Rung.valueOf(rung), actor.client.appliedRung)
    }

    @Then("it offers exactly {string}")
    fun offers(names: String) {
        assertEquals(names.split(",").map { it.trim() }.toSet(), held.affordances.keys)
    }

    @Then("every shared layer it fetched matched the integrity of its link")
    fun layersMatched() {
        // A mismatch would have thrown before the representation was returned.
        val shared = actor.log.filter { SHARED.containsMatchIn(it.url) }
        if (actor.client.appliedRung != Rung.L0) assertTrue(shared.isNotEmpty(), "no shared layer was fetched")
    }

    @Then("the act carried If-Match with the validator it read")
    fun carriedValidator() {
        val act = assertNotNull(actor.log.firstOrNull { it.method == "POST" }, "no act was sent")
        assertEquals(validatorBefore, act.header("If-Match"))
    }

    @Then("it was refused as {string} with status {int}")
    fun refusedAs(kind: String, status: Int) {
        assertEquals(kind to status, kindOf(assertNotNull(error, "expected a refusal")))
    }

    @Then("the refusal says {string}")
    fun refusalSays(text: String) {
        val said = assertNotNull(error, "expected a refusal").message.orEmpty()
        assertTrue(text in said, "the refusal said: $said")
    }

    @Then("the water act was sent once")
    fun wateredOnce() {
        assertEquals(1, actor.log.count { it.method == "POST" && "water-cell" in it.url })
    }

    private fun nothingSent() {
        assertEquals(emptyList(), actor.log.drop(sentBefore).filter { it.method != "GET" }, "something was sent")
    }

    @Then("it was refused before sending, because {string}")
    fun refusedBeforeSending(@Suppress("UNUSED_PARAMETER") text: String) {
        assertNotNull(error, "expected a refusal")
        nothingSent()
    }

    @Then("it was refused as {string} before sending, because {string}")
    fun refusedAsBeforeSending(kind: String, @Suppress("UNUSED_PARAMETER") text: String) {
        assertEquals(kind, kindOf(assertNotNull(error, "expected a refusal")).first)
        nothingSent()
    }

    @Given("a service whose affordance set does not match the integrity of its link")
    fun mismatchedService() {
        bookmark = FAKE
        service = FakeServices.mismatchedSet()
    }

    @Given("a service whose resource embeds its author, and keeps a member named {string}")
    fun authorService(member: String) {
        bookmark = FAKE
        service = FakeServices.embeddedAuthor(member)
    }

    @When("it tries to enter that service")
    fun tryEnterService() = trying { actor.client.enter(bookmark) }

    @When("it enters that service")
    fun enterService() {
        rep = actor.client.enter(bookmark)
    }

    @Then("it refused the affordance set for its integrity")
    fun refusedForIntegrity() {
        assertIs<IntegrityException>(error)
    }

    @Then("it failed, saying {string}")
    fun failed(@Suppress("UNUSED_PARAMETER") text: String) {
        assertNotNull(error, "expected a failure")
    }

    @Then("it holds these embedded resources")
    fun embedded(table: DataTable) {
        val rows = held.embeddedByRel.flatMap { (rel, items) ->
            items.map { mapOf("relation" to rel, "title" to it.title.orEmpty(), "status" to status(it.state)) }
        }
        assertEquals(table.asMaps(), rows)
    }

    private fun status(state: JsonObject) = state["status"]?.jsonPrimitive?.content.toString()

    @Then("it read no bed on its own")
    fun readNoBed() {
        assertEquals(emptyList(), actor.log.filter { BED.containsMatchIn(it.url) })
    }

    @Then("the state has exactly these members")
    fun members(table: DataTable) {
        assertEquals(table.asMaps().map { it.getValue("member") }, held.state.keys.toList())
    }

    private fun kindOf(refusal: Exception): Pair<String, Int> = when (refusal) {
        is StaleAct -> "stale" to HTTP_STALE
        is IllegalTransition -> "not-offered" to HTTP_CONFLICT
        is ValidatorRequired -> "precondition-required" to HTTP_PRECONDITION
        is NotAuthenticated -> "not-authenticated" to HTTP_UNAUTHENTICATED
        is RuleRefused -> "rule-refused" to HTTP_UNPROCESSABLE
        is InvalidPayload -> "invalid-input" to HTTP_UNPROCESSABLE
        is NotAChoice -> "not-a-choice" to 0
        else -> "failed" to 0
    }

    private companion object {
        const val CREDENTIAL = "Bearer fixture"
        val SHARED = Regex("/(affordance-sets|schemas)/")
        val BED = Regex("/beds/\\d+$")
        const val HTTP_UNAUTHENTICATED = 401
        const val HTTP_CONFLICT = 409
        const val HTTP_STALE = 412
        const val HTTP_UNPROCESSABLE = 422
        const val HTTP_PRECONDITION = 428
    }
}
