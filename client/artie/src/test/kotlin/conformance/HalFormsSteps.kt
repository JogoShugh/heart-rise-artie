package conformance

import com.artie.heart.sims.Logged
import com.artie.heart.sims.Simulator
import heart.Dialect
import heart.HeartRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals

/** Steps for hal-forms.feature: ARTIE asking for HAL-FORMS, and what it sends from a template. */
class HalFormsSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("a HAL-FORMS client with the garden's credential")
    fun gardenClient() {
        world.me = world.client("L0", GARDEN_CREDENTIAL, Dialect.HAL_FORMS)
    }

    @Given("another HAL-FORMS client with the garden's credential")
    fun anotherGardenClient() {
        world.other = world.client("L0", GARDEN_CREDENTIAL, Dialect.HAL_FORMS)
    }

    @Given("a HAL-FORMS client at rung {word} with the {word} service's credential")
    fun simulatedClient(rung: String, service: String) {
        world.me = world.simulatedClient(rung, service, Dialect.HAL_FORMS)
    }

    @When("the other client waters bed {int} with {int} ml, in HAL-FORMS")
    fun otherWaters(bed: Int, ml: Int) {
        val client = checkNotNull(world.other).client
        client.enter(world.bookmark)
        client.act("getBed", buildJsonObject { put("id", bed) })
        client.follow("item", "bed-$bed")
        client.act("waterCell", buildJsonObject { put("milliliters", ml) })
    }

    @Then("the last read accepted {string}, at the HAL-FORMS profile, with no cache")
    fun acceptedHalForms(accept: String) {
        val read = lastRead()
        assertEquals(
            listOf(accept, "<${Dialect.HAL_FORMS.profile}>", "no-cache"),
            listOf(read.header("Accept"), read.header("Accept-Profile"), read.header("Cache-Control")),
        )
    }

    @Given("the build service's job {string} has the HAL-FORMS parameters {}")
    fun halFormsParameters(job: String, properties: String) {
        val template = buildJsonObject {
            put("method", "POST")
            put("target", "/jobs/$job/actions/build")
            put("contentType", "application/json")
            put("properties", Json.parseToJsonElement(properties))
        }
        world.sims.faults(Simulator.BUILD).template("build", template.toString())
    }

    @Given("the {word} service serves only HAL Schema Forms")
    fun onlyReferenceDialect(service: String) {
        world.sims.faults("$service.test").onlyAccept = Dialect.HAL_SCHEMA_FORMS.mediaType
    }

    @Given("the {word} service serves {string} with status {int} and the body {}")
    fun serves(service: String, path: String, status: Int, body: String) =
        world.sims.faults("$service.test").serve(path, status, body)

    @Then("the last request was {word} with the body {string}")
    fun lastRequestWith(method: String, body: String) {
        val last = actRequest(world.sims.log)
        assertEquals(method to body, last.method to last.body)
    }

    @Then("it had the content type {string}")
    fun contentType(type: String) = assertEquals(type, actRequest(world.sims.log).headers["Content-Type"].orEmpty())

    /** The last read of a resource itself: shared layers, read by their links, are not counted. */
    private fun lastRead(): HeartRequest = actor.log.last { it.method == "GET" && "/affordance-sets/" !in it.url }

    private companion object {
        const val GARDEN_CREDENTIAL = "Bearer fixture"
    }
}

/**
 * An act's own request, which a scenario's "the last request" means: the last one the simulators took, less any
 * read again after it of a resource read before. A client reads again after a refused act, to learn whether the
 * form it acted on was out of date.
 */
internal fun actRequest(log: List<Logged>): Logged {
    var at = log.lastIndex
    while (at > 0 && readAgain(log, at)) at--
    return log[at]
}

/** Whether the request at [at] reads a resource the log shows read before it. */
private fun readAgain(log: List<Logged>, at: Int): Boolean =
    log[at].method == "GET" && log.subList(0, at).any { it.method == "GET" && it.url == log[at].url }
