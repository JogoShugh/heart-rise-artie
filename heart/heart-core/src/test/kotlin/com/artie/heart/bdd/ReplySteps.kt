package com.artie.heart.bdd

import com.artie.heart.ActPipeline
import com.artie.heart.NotOfferedException
import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.UnprocessableContentException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals

/** Ana's reply, acted on through heart-core's act pipeline. A reply's validator is `v<version>`. */
class ReplySteps {
    private val store = ReplyStore()
    private val resource by lazy { Replies.resource(store) }
    private val pipeline by lazy { ActPipeline(resource.layers.type) }
    private var outcome: Result<Unit>? = null

    @Given("""^reply (r-\w+) is awaiting$""")
    fun awaiting(reference: String) = store.open(reference)

    @Given("""^reply (r-\w+) was completed with the body (.*)$""")
    fun completed(reference: String, body: String) {
        sendAct(reference, "completed", "current", body)
        checkNotNull(outcome).getOrThrow()
    }

    @When("""^a client acts on reply (r-\w+) with "(\w+)", validator (none|stale|current), and body (.*)$""")
    fun acts(reference: String, act: String, validator: String, body: String) =
        sendAct(reference, act, validator, body)

    @Then("^the reply act was performed$")
    fun performed() {
        checkNotNull(outcome).getOrThrow()
    }

    @Then("""^the reply act is refused as not offered "([^"]+)"$""")
    fun notOffered(curie: String) {
        val failure = checkNotNull(checkNotNull(outcome).exceptionOrNull()) { "the act was not refused" }
        check(failure is NotOfferedException) { "expected NotOfferedException, got $failure" }
        assertEquals(resource.layers.type.profile.reference(curie.substringAfter(':')), failure.rel)
    }

    @Then("""^the reply act is refused as invalid at "([^"]+)"$""")
    fun invalid(pointer: String) {
        val failure = checkNotNull(checkNotNull(outcome).exceptionOrNull()) { "the act was not refused" }
        check(failure is UnprocessableContentException) { "expected UnprocessableContentException, got $failure" }
        assertEquals(listOf(pointer), failure.violations.map { it.pointer }.distinct())
    }

    @Then("""^reply (r-\w+) is now (\w+)$""")
    fun now(reference: String, standing: String) {
        assertEquals(standing, checkNotNull(store.get(reference)) { "no reply $reference" }.standing)
    }

    /** Sends [act] on the reply [reference] with [validator] (none, stale or current), and keeps the outcome. */
    private fun sendAct(reference: String, act: String, validator: String, body: String) {
        val sent = when (validator) {
            "none" -> null
            "stale" -> "v0"
            else -> "v${checkNotNull(store.get(reference)).version}"
        }
        outcome = runCatching {
            runBlocking {
                val current = { checkNotNull(store.get(reference)) }
                val checked = pipeline.check(act, sent, current, body = body) { reply, v -> v == "v${reply.version}" }
                pipeline.run(act, body, checked) { input -> resource.perform(checked, act, input) }
            }
        }
    }
}
