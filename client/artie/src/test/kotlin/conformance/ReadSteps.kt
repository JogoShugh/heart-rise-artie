package conformance

import com.artie.heart.sims.Simulator
import heart.ActRefused
import heart.ArtieClient
import heart.CachingTransport
import heart.InvalidPayload
import heart.IntegrityCheck
import heart.RuleRefused
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Steps for refusals.feature and reads.feature: what a client makes of what a service answers. */
class ReadSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("the {word} service answers {string} with status {int} and the body {}")
    fun answers(service: String, form: String, status: Int, body: String) =
        world.sims.faults("$service.test").answerAct(form.substringAfter(':'), status, body)

    @Given("the {word} service refuses every read with status {int}")
    fun refusesReads(service: String, status: Int) {
        world.sims.faults("$service.test").readStatus = status
    }

    @Given("the {word} service sends no validator")
    fun noValidator(service: String) {
        world.sims.faults("$service.test").noValidator = true
    }

    @Given("a client with no options but the {word} service's credential")
    fun plainClient(service: String) {
        world.simulated()
        val log = mutableListOf<heart.HeartRequest>()
        val client = ArtieClient(CachingTransport(IntegrityCheck(world.wire(log, CREDENTIALS.getValue(service)))))
        world.me = Actor(client, log, mutableListOf(), mutableListOf())
    }

    @When("it tries to enter the job {string}")
    fun tryEnterJob(name: String) = world.trying { actor.client.enter("http://${Simulator.BUILD}/jobs/$name") }

    /** ARTIE reads a resource again by entering it again; before any, there is nothing to read again. */
    @When("it tries to read the resource again")
    fun tryReadAgain() = world.trying {
        actor.client.enter(actor.client.current?.url ?: error("enter a service first"))
    }

    /** ARTIE answers no choices for a form it is not offered; said here in the features' words. */
    @When("it tries to read the choices of {string}")
    fun tryChoices(form: String) = world.trying {
        checkNotNull(actor.client.choices(form)) { "$form is not offered now" }
        world.held
    }

    @Then("the resource was read {int} times")
    fun readTimes(times: Int) = assertEquals(
        times,
        world.sims.log.count { it.method == "GET" && !SHARED.containsMatchIn(it.url) },
    )

    @Then("it names no violations")
    fun noViolations() = assertEquals(emptyList(), violations())

    @Then("the only violation is {string}")
    fun onlyViolation(text: String) = assertEquals(listOf(text), violations())

    @Then("the affordance set was read accepting {string}")
    fun setAccepted(accept: String) = assertEquals(
        accept,
        assertNotNull(world.sims.log.firstOrNull { it.url.startsWith("/affordance-sets/") }).headers["Accept"],
    )

    @Then("it carried an empty validator")
    fun emptyValidator() = assertEquals("", world.sims.log.last { it.method != "GET" }.headers["If-Match"])

    @Then("the last read accepted {string}, at the profile, with no cache")
    fun acceptedReference(accept: String) {
        val read = actor.log.last { it.method == "GET" && "/affordance-sets/" !in it.url }
        assertEquals(
            listOf(accept, "<${heart.HAL_SCHEMA_FORMS_PROFILE}>", "no-cache"),
            listOf(read.header("Accept"), read.header("Accept-Profile"), read.header("Cache-Control")),
        )
    }

    private fun violations(): List<String> = when (val refusal = assertNotNull(world.error, "expected a refusal")) {
        is RuleRefused -> refusal.violations
        is InvalidPayload -> refusal.violations
        is ActRefused -> emptyList()
        else -> error("not a refusal of a body: $refusal")
    }

    private companion object {
        val SHARED = Regex("^/(affordance-sets|schemas)/")
    }
}
