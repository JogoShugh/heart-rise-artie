package conformance

import com.artie.heart.sims.Simulator
import heart.OutsideSchema
import io.cucumber.datatable.DataTable
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

/** Steps for the simulated services (heart-sims), as the shared features name them. */
class SimulatedServiceSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("a client at rung {word} with the {word} service's credential")
    fun credentialed(rung: String, service: String) {
        world.me = world.simulatedClient(rung, service)
    }

    @Given("the build service has a job {string} whose parameters are {}")
    fun job(name: String, parameters: String) = world.sims.configureJob(name, parameters)

    @When("the job {string} is given the parameters {}")
    fun reconfigured(name: String, parameters: String) = world.sims.configureJob(name, parameters)

    @Given("the build service declares the parameters of its job {string} as {}, without checking them")
    fun declaredUnchecked(@Suppress("UNUSED_PARAMETER") name: String, parameters: String) =
        world.sims.faults(Simulator.BUILD).declareSchema(BUILD_FORM, parameters)

    @Given("the build service refuses {string} as invalid input, saying {string}")
    fun refuses(form: String, detail: String) {
        val problem = """{"type": "$PROBLEMS/invalid-input", "title": "Invalid input", "detail": "$detail"}"""
        world.sims.faults(Simulator.BUILD).answerAct(segmentOf(form), HTTP_UNPROCESSABLE, problem)
    }

    @When("it enters the job {string}")
    fun enterJob(name: String) {
        world.rep = actor.client.enter("${origin("build")}/jobs/$name")
    }

    @When("it reads the resource again")
    fun readAgain() {
        world.rep = actor.client.enter(world.held.url)
    }

    @Then("the act {string} was sent once, with exactly {}")
    fun sentExactly(form: String, body: String) {
        val acts = actsOn(form)
        assertEquals(1, acts.size)
        assertEquals(Json.parseToJsonElement(body), Json.parseToJsonElement(acts.single().body))
    }

    @Then("the act {string} was sent {int} times")
    fun sentTimes(form: String, times: Int) = assertEquals(times, actsOn(form).size)

    @Then("it was refused as {string} before sending, with the one violation {string}")
    fun refusedWithOne(kind: String, violation: String) = assertEquals(listOf(violation), refusedBeforeSending(kind))

    @Then("it was refused as {string} before sending, with the violations")
    fun refusedWith(kind: String, table: DataTable) =
        assertEquals(table.asList(), refusedBeforeSending(kind))

    @Then("no schema failure was noted")
    fun noSchemaFailure() = assertEquals(emptyList(), schemaFailures())

    @Then("the local log holds one schema failure, for {string}")
    fun oneSchemaFailure(form: String) {
        val failure = schemaFailures().single()
        assertEquals(form, failure.getValue("attributes").jsonObject["heart.rel"]?.jsonPrimitive?.content)
    }

    /** The refusal's violations, once it is known to be of [kind], to say nothing was sent, and to be so. */
    private fun refusedBeforeSending(kind: String): List<String> {
        val refusal = assertNotNull(world.error, "expected a refusal")
        assertEquals(kind, Kinds.of(refusal).first)
        assertTrue("nothing was sent" in refusal.message.orEmpty(), refusal.message)
        assertEquals(emptyList(), actor.log.drop(world.sentBefore).filter { it.method != "GET" }, "something was sent")
        return assertIs<OutsideSchema>(refusal).violations
    }

    private fun schemaFailures(): List<JsonObject> =
        actor.events.filter { it["event_name"]?.jsonPrimitive?.content == "schema.uncompiled" }

    /** The acts on [form] the simulated services received, in order. */
    private fun actsOn(form: String) =
        world.sims.log.filter { it.method != "GET" && it.url.endsWith("/actions/${segmentOf(form)}") }

    private companion object {
        const val BUILD_FORM = "ci:build"
        const val PROBLEMS = "https://w3id.org/heart-rise/problems"
        const val HTTP_UNPROCESSABLE = 422

        /** The act a form names, as its target's last segment: "ci:build" is acted on at .../actions/build. */
        fun segmentOf(form: String) = form.substringAfter(':')
    }
}
