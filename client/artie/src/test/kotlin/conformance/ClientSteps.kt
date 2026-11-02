package conformance

import heart.IntegrityException
import heart.Rung
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
 * public client. heart-ts has its own steps file with the same phrases.
 */
class ClientSteps(private val world: World) {
    private val actor get() = world.actor
    private val held get() = world.held

    @Before
    fun name(scenario: Scenario) {
        world.scenarioName = scenario.name
    }

    @Given("the garden is at its seed state")
    fun seed() {
        world.service = GardenReplay.forScenario(world.scenarioName)
    }

    @Given("a client at rung {word} with the garden's credential")
    fun credentialed(rung: String) {
        world.me = world.client(rung, CREDENTIAL)
    }

    @Given("another client at rung {word} with the garden's credential")
    fun anotherCredentialed(rung: String) {
        world.other = world.client(rung, CREDENTIAL)
    }

    @Given("a client at rung {word} with no credential")
    fun anonymous(rung: String) {
        world.me = world.client(rung, null)
    }

    private fun fields(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject

    @When("it enters at the bookmark")
    fun enter() {
        world.rep = actor.client.enter(world.bookmark)
    }

    @When("it tries to enter at the bookmark")
    fun tryEnter() = world.trying { actor.client.enter(world.bookmark) }

    @When("it submits {string} with {}")
    fun submit(form: String, body: String) {
        world.validatorBefore = actor.client.current?.etag
        world.rep = actor.client.act(form, fields(body))
    }

    @When("it tries to submit {string} with {}")
    fun trySubmit(form: String, body: String) {
        world.sentBefore = actor.log.size
        world.trying { actor.client.act(form, fields(body)) }
    }

    @When("the other client waters bed {int} with {int} ml")
    fun otherWaters(bed: Int, ml: Int) {
        val client = checkNotNull(world.other).client
        client.enter(world.bookmark)
        client.act("garden:get-bed", fields("""{"id": $bed}"""))
        client.act("garden:water-cell", fields("""{"milliliters": $ml}"""))
    }

    @When("it follows the {string} link titled {string}")
    fun follow(rel: String, title: String) {
        world.rep = actor.client.follow(rel, title)
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

    @Then("it offers exactly these acts")
    fun offersActs(table: io.cucumber.datatable.DataTable) =
        assertEquals(table.asList(), world.held.affordances.keys.toList())

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
        assertEquals(world.validatorBefore, act.header("If-Match"))
    }

    @Then("it was refused as {string} with status {int}")
    fun refusedAs(kind: String, status: Int) {
        assertEquals(kind to status, Kinds.of(assertNotNull(world.error, "expected a refusal")))
    }

    @Then("the refusal says {string}")
    fun refusalSays(text: String) {
        // What a refusal says is its message and each violation it names, as every client's steps read it.
        val refusal = assertNotNull(world.error, "expected a refusal")
        val violations = when (refusal) {
            is heart.RuleRefused -> refusal.violations
            is heart.InvalidPayload -> refusal.violations
            else -> emptyList()
        }
        val said = (listOf(refusal.message.orEmpty()) + violations).joinToString(" ")
        assertTrue(text in said, "the refusal said: $said")
    }

    @Then("the water act was sent once")
    fun wateredOnce() {
        assertEquals(1, actor.log.count { it.method == "POST" && "water-cell" in it.url })
    }

    private fun nothingSent() {
        assertEquals(emptyList(), actor.log.drop(world.sentBefore).filter { it.method != "GET" }, "something was sent")
    }

    @Then("it was refused before sending, because {string}")
    fun refusedBeforeSending(text: String) {
        val said = assertNotNull(world.error, "expected a refusal").message.orEmpty()
        assertTrue(text in said, "it said: $said")
        nothingSent()
    }

    @Then("it was refused as {string} before sending, because {string}")
    fun refusedAsBeforeSending(kind: String, text: String) {
        val refusal = assertNotNull(world.error, "expected a refusal")
        assertEquals(kind, Kinds.of(refusal).first)
        assertTrue(text in refusal.message.orEmpty(), "it said: ${refusal.message}")
        nothingSent()
    }

    @Given("a service whose affordance set does not match the integrity of its link")
    fun mismatchedService() {
        world.bookmark = FAKE
        world.service = FakeServices.mismatchedSet()
    }

    @Given("a service whose resource embeds its author, and keeps a member named {string}")
    fun authorService(member: String) {
        world.bookmark = FAKE
        world.service = FakeServices.embeddedAuthor(member)
    }

    @When("it tries to enter that service")
    fun tryEnterService() = world.trying { actor.client.enter(world.bookmark) }

    @When("it enters that service")
    fun enterService() {
        world.rep = actor.client.enter(world.bookmark)
    }

    @Then("it refused the affordance set for its integrity")
    fun refusedForIntegrity() {
        assertIs<IntegrityException>(world.error)
    }

    @Then("it failed, saying {string}")
    fun failed(text: String) {
        val said = assertNotNull(world.error, "expected a failure").message.orEmpty()
        assertTrue(text in said, "it said: $said")
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

    private companion object {
        const val CREDENTIAL = "Bearer fixture"
        val SHARED = Regex("/(affordance-sets|schemas)/")
        val BED = Regex("/beds/\\d+$")
    }
}
