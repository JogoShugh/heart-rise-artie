package conformance

import com.artie.heart.Rung as ServedRung
import com.artie.heart.sims.SetLink
import com.artie.heart.sims.Simulator
import heart.CachingTransport
import heart.Dialect
import heart.HeartRequest
import heart.Rung
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Steps for rungs.feature: what a client asks for, what it is told, and the shared layers it reads. */
class RungSteps(private val world: World) {
    private val actor get() = world.actor
    private val other get() = checkNotNull(world.other) { "no other client" }

    @Then("the last read asked for {string}")
    fun askedFor(prefer: String) = assertEquals(prefer, lastRead().header("Prefer"))

    @Then("the last read asked for no layering")
    fun askedForNone() = assertNull(lastRead().header("Prefer"))

    @Then("the representation is at rung L0, in {}")
    fun inDialect(dialect: String) {
        assertEquals(Rung.L0, actor.client.appliedRung)
        val (_, answer) = actor.answers.last { (asked, _) ->
            asked.method == "GET" && !SHARED.containsMatchIn(asked.url)
        }
        val type = answer.header("Content-Type").orEmpty().substringBefore(';').trim()
        assertEquals(DIALECTS.getValue(dialect), type)
        assertTrue(world.held.affordances.isNotEmpty(), "no form was read")
    }

    @Then("no shared layer was read twice")
    fun noSharedTwice() {
        val shared = actor.log.filter { SHARED.containsMatchIn(it.url) }.map { it.url }
        assertTrue(shared.isNotEmpty(), "no shared layer was read")
        assertEquals(shared.distinct(), shared)
    }

    @When("the other client reads bed {int}")
    fun otherReadsBed(bed: Int) {
        other.client.enter(world.bookmark)
        other.client.act("garden:get-bed", buildJsonObject { put("id", JsonPrimitive(bed)) })
    }

    @Then("the two clients read different affordance sets")
    fun differentSets() {
        val mine = setsRead(actor.log)
        assertTrue(mine.isNotEmpty())
        assertEquals(emptyList(), mine.filter { it in setsRead(other.log) })
    }

    @Given("a HAL-FORMS client with the {word} service's credential")
    fun halForms(service: String) {
        world.simulated()
        world.me = world.client("L0", CREDENTIALS.getValue(service), Dialect.HAL_FORMS)
    }

    @Given("two clients at rung L1 with the {word} service's credential and {}")
    fun pair(service: String, caches: String) {
        world.simulated()
        val token = CREDENTIALS.getValue(service)
        val shared = CachingTransport(world.wire(mutableListOf(), token))
        val cache: ((heart.Transport) -> heart.Transport)? = when (caches) {
            "one cache between them" -> { _ -> shared }
            "a cache each" -> { wire -> CachingTransport(wire) }
            "one cache that keeps nothing" -> null
            else -> error("no such caches: $caches")
        }
        world.me = world.client("L1", token, cache = cache)
        world.other = world.client("L1", token, cache = cache)
    }

    @When("it tries to enter the app {string}")
    fun tryEnterApp(name: String) = world.trying { actor.client.enter(appUrl(name)) }

    @When("the other client enters the app {string}")
    fun otherEntersApp(name: String) {
        other.client.enter(appUrl(name))
    }

    @Given("the deploy service serves up to rung {word}")
    fun servesUpTo(rung: String) = world.sims.deployServesUpTo(ServedRung.valueOf(rung))

    @Given("the deploy service's app {string} starts deploying")
    fun startsDeploying(app: String) = world.sims.startDeploying(app)

    @Given("the {word} service says it applied {string}")
    fun saysApplied(service: String, applied: String) {
        faults(service).applied = applied
    }

    @Given("the {word} service links its affordance set as {string}, with the curie {string} for {string}")
    fun linksAs(service: String, rel: String, curie: String, href: String) {
        faults(service).setLink = SetLink(rel, curie, href)
    }

    @Given("the {word} service links its affordance set as {string}, with no curie")
    fun linksAsWithNoCurie(service: String, rel: String) {
        faults(service).setLink = SetLink(rel)
    }

    @Given("the {word} service links its affordance set with an integrity its bytes do not match")
    fun wrongIntegrity(service: String) {
        faults(service).wrongIntegrity = true
    }

    @Given("the {word} service serves every affordance set at one address")
    fun oneAddress(service: String) {
        faults(service).oneAddress = true
    }

    @Given("the {word} service names no profile")
    fun noProfile(service: String) {
        faults(service).noProfile = true
    }

    @Given("the {word} service's media type is {string}")
    fun mediaType(service: String, type: String) {
        faults(service).mediaType = type
    }

    @Given("the {word} service's template {string} is {}")
    fun template(service: String, name: String, template: String) = faults(service).template(name, template)

    @Then("the affordance set was read {int} times")
    fun setReads(times: Int) =
        assertEquals(times, world.sims.log.count { it.method == "GET" && it.url.startsWith("/affordance-sets/") })

    @Then("it sent {word} for {string}, with no body")
    fun sentWithNoBody(method: String, url: String) =
        assertEquals(listOf(""), world.sims.log.filter { it.method == method && it.url == url }.map { it.body })

    @Then("the last request was for {string}")
    fun lastRequest(url: String) = assertEquals(url, world.sims.log.last().url)

    private fun faults(service: String) = world.sims.faults("$service.test")

    private fun appUrl(name: String) = "http://${Simulator.DEPLOY}/apps/$name"

    /** The last read of a resource itself: shared layers, read by their links, are not counted. */
    private fun lastRead(): HeartRequest = actor.log.last { it.method == "GET" && !SHARED.containsMatchIn(it.url) }

    private fun setsRead(log: List<HeartRequest>) = log.map { it.url }.filter { "/affordance-sets/" in it }

    private companion object {
        val SHARED = Regex("/(affordance-sets|schemas)/")

        /** Each dialect, as the features name it, by the media type it is served in. */
        val DIALECTS = mapOf(
            "HAL-FORMS" to Dialect.HAL_FORMS.mediaType,
            "HAL Schema Forms" to Dialect.HAL_SCHEMA_FORMS.mediaType,
        )
    }
}
