package com.artie.heart.tck

import com.artie.heart.ActPipeline
import com.artie.heart.Caller
import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.StaleWriteConflict
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/**
 * The domain's side of SPEC §6.2's stale refusal. A binding reads the
 * resource, checks the client's validator against it, and hands that state
 * to the domain's act. Here another client's act lands between the read and
 * the act, as it can on a busy server, and the domain must refuse to act on
 * the state it was given.
 */
class TckStaleActSteps(
    private val fixture: TckFixture,
    private val world: TckWorld,
    private val resources: List<HeartResource<*, *>>,
    private val mapper: ObjectMapper,
) {
    private var read: Any? = null
    private var otherETag: String? = null
    private var refusal: Throwable? = null

    private val asSchemaForms = mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType)

    @Suppress("UNCHECKED_CAST")
    private val resource: HeartResource<Any?, Any?>
        get() = resources.single { it.layers.type.collection == fixture.collection } as HeartResource<Any?, Any?>

    private val offeredAct: String
        get() = resource.layers.type.acts.single { it.path == fixture.offered.path }.name

    @When("the server reads the resource to check the offered act")
    fun readForAct() {
        // As a binding does, the domain reads as the request's caller (Caller).
        read = Caller.during(fixture.credential) { runBlocking { resource.locate(fixture.variables(world.id)) } }
    }

    @When("another client's offered act lands before it is performed")
    fun otherClientActs() {
        world.hold(asSchemaForms)
        val ifMatch = HttpHeaders.IF_MATCH to checkNotNull(world.heldETag)
        val target = "${world.resource}/actions/${fixture.offered.path}"
        val answer = world.post(target, fixture.offered.body, asSchemaForms + ifMatch)
        assertEquals(HttpStatus.OK, answer.status, "the other client's act was refused")
        otherETag = answer.responseHeaders.eTag
    }

    @When("the server performs the offered act on the state it read")
    fun performOnRead() {
        val pipeline = ActPipeline(resource.layers.type, mapper)
        // As a binding does, the domain's act runs as the request's caller (Caller).
        refusal = runCatching {
            Caller.during(fixture.credential) {
                runBlocking {
                    pipeline.run(offeredAct, fixture.offered.body) { resource.perform(read, offeredAct, it) }
                }
            }
        }.exceptionOrNull()
    }

    /** The pipeline turns the domain's [com.artie.heart.ConcurrentWriteException] into the stale refusal, 412. */
    @Then("the act is refused as stale")
    fun refusedAsStale() =
        assertTrue(refusal is StaleWriteConflict, "not refused as stale: ${refusal ?: "it was performed"}")

    @Then("the resource holds only the other client's act")
    fun onlyTheOtherAct() {
        val now = world.get(world.resource, asSchemaForms).responseHeaders.eTag
        assertEquals(otherETag, now, "the resource changed after the other client's act")
    }
}
