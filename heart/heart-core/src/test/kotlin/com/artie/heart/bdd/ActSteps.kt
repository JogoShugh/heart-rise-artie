package com.artie.heart.bdd

import com.artie.heart.ActPipeline
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Dialect
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotOfferedException
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.ServiceIndex
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Acts on lamps through heart-core's act pipeline. A lamp's validator is `v<version>`. */
class ActSteps(private val world: LampWorld) {
    private var outcome: Result<Unit>? = null
    private var concurrentWriter = false

    @Given("someone else writes to lamp {long} while the next act runs")
    fun someoneElseWrites(id: Long) {
        check(id in world.lamps)
        concurrentWriter = true
    }

    @When("^a client acts on lamp (\\d+) with \"(\\w+)\", validator (none|stale|current), and body (.*)$")
    fun act(id: Long, act: String, validator: String, body: String) {
        val pipeline = ActPipeline(world.type)
        val sent = when (validator) {
            "none" -> null
            "stale" -> "v0"
            else -> "v${world.lamps.getValue(id).version}"
        }
        outcome = runCatching {
            runBlocking {
                pipeline.check(act, sent, { world.lamps.getValue(id) }) { lamp, v -> v == "v${lamp.version}" }
                pipeline.run(act, body) { input ->
                    if (concurrentWriter) throw ConcurrentWriteException("lamp $id was written first")
                    val lamp = world.lamps.getValue(id)
                    world.lamps[id] = when (act) {
                        "dim" -> lamp.copy(brightness = checkNotNull(input.bind(Dim::class.java).brightness))
                        "switchOff" -> lamp.copy(status = "OFF")
                        else -> lamp.copy(status = "ON")
                    }.let { it.copy(version = it.version + 1) }
                }
            }
        }
    }

    /** A creation's owning resource is the service index, so its validator is the index's. */
    @When("^a client creates a lamp with validator (none|stale|current) and body (.*)$")
    fun create(validator: String, body: String) {
        val pipeline = ActPipeline(world.type)
        val index = ServiceIndex(listOf(world.type)).validator(Dialect.JSONSCHEMA)
        val sent = when (validator) {
            "none" -> null
            "stale" -> "\"index-old\""
            else -> index
        }
        val creation = checkNotNull(world.type.creation)
        outcome = runCatching {
            runBlocking {
                pipeline.checkCreation(sent) { it == index }
                pipeline.run(creation.name, body) { input ->
                    val id = world.lamps.size + 1L
                    val brightness = checkNotNull(input.bind(InstallLamp::class.java).brightness)
                    world.lamps[id] = Lamp(id, "OFF", brightness, version = 1)
                }
            }
        }
    }

    @Then("no lamp was created")
    fun noneCreated() = assertTrue(world.lamps.isEmpty(), "lamps: ${world.lamps}")

    @Then("the act was performed")
    fun performed() {
        checkNotNull(outcome).getOrThrow()
    }

    @Then("the act is refused as precondition required")
    fun preconditionRequired() = refusedAs<PreconditionRequiredException>()

    @Then("the act is refused as stale")
    fun stale() = refusedAs<StaleWriteConflict>()

    @Then("the act is refused as malformed")
    fun malformed() = refusedAs<MalformedActBodyException>()

    @Then("the act is refused as not offered {string}")
    fun notOffered(curie: String) {
        val failure = refusedAs<NotOfferedException>()
        assertEquals(world.profile.reference(curie.substringAfter(':')), failure.rel)
    }

    @Then("the act is refused as invalid at {string}")
    fun invalid(pointer: String) {
        val failure = refusedAs<UnprocessableContentException>()
        assertEquals(listOf(pointer), failure.violations.map { it.pointer }.distinct())
    }

    @Then("lamp {long} is still {string} at version {int}")
    fun still(id: Long, status: String, version: Int) {
        val lamp = world.lamps.getValue(id)
        assertEquals(status to version, lamp.status to lamp.version)
    }

    @Then("lamp {long} is {string} with brightness {int} at version {int}")
    fun lampIs(id: Long, status: String, brightness: Int, version: Int) =
        assertEquals(Lamp(id, status, brightness, version), world.lamps.getValue(id))

    private inline fun <reified T : Throwable> refusedAs(): T {
        val failure = checkNotNull(outcome).exceptionOrNull()
        check(failure is T) { "expected ${T::class.simpleName}, got $outcome" }
        return failure
    }
}
