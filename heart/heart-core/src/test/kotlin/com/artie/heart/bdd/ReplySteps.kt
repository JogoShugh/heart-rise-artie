package com.artie.heart.bdd

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.ItemActs
import com.artie.heart.NotOfferedException
import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.UnprocessableContentException
import com.artie.heart.UnsupportedMediaTypeException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals

/** Ana's replies, the shape of result their owner wants, and the outcome of the last act on one. */
class ReplyWorld {
    val store = ReplyStore()
    var result: String = Replies.ANY_RESULT
    val resource by lazy { Replies.resource(store, result) }
    val pipeline by lazy { ActPipeline(resource.layers.type) }
    var outcome: Result<Unit>? = null

    /** The last act's refusal, as a [T]. */
    inline fun <reified T : Throwable> refused(): T {
        val failure = checkNotNull(checkNotNull(outcome).exceptionOrNull()) { "the act was not refused" }
        check(failure is T) { "expected ${T::class.simpleName}, got $failure" }
        return failure
    }
}

/** Ana's reply, acted on through heart-core's act pipeline. A reply's validator is `v<version>`. */
class ReplySteps(private val world: ReplyWorld) {
    private var chosen: Result<Act>? = null

    @Given("""^replies take a result shaped as (.*)$""")
    fun resultShaped(schema: String) {
        world.result = schema
    }

    @Given("""^reply (r-\w+) is awaiting$""")
    fun awaiting(reference: String) = world.store.open(reference)

    @Given("""^reply (r-\w+) (?:was completed with the body|took the progress) (.*)$""")
    fun took(reference: String, body: String) {
        sendAct(reference, ItemActs.choose(world.resource.layers.type, Act.PATCH, typeOf(body), body).name, body)
        checkNotNull(world.outcome).getOrThrow()
    }

    @When("""^a client acts on reply (r-\w+) with "(\w+)", validator (none|stale|current), and body (.*)$""")
    fun acts(reference: String, act: String, validator: String, body: String) =
        sendAct(reference, act, body, validator)

    @When("""^a PATCH of (\S+) with the state "(\w+)" reaches reply (r-\w+)$""")
    fun patch(type: String, state: String, @Suppress("UNUSED_PARAMETER") reference: String) {
        chosen = runCatching {
            ItemActs.choose(world.resource.layers.type, Act.PATCH, type, """{"state": "$state"}""")
        }
    }

    @Then("""^the reply takes it as (?:the act "(\w+)"|invalid at "([^"]+)"|needing the type "([^"]+)")$""")
    fun taken(act: String?, pointer: String?, type: String?) {
        val outcome = checkNotNull(chosen)
        when {
            act != null -> assertEquals(act, outcome.getOrThrow().name)
            pointer != null -> {
                val failure = outcome.exceptionOrNull()
                check(failure is UnprocessableContentException) { "expected a 422, got $outcome" }
                assertEquals(listOf(pointer), failure.violations.map { it.pointer })
            }
            else -> {
                val failure = outcome.exceptionOrNull()
                check(failure is UnsupportedMediaTypeException) { "expected a 415, got $outcome" }
                check(type in failure.accepted) { "$type is not among ${failure.accepted}" }
            }
        }
    }

    @Then("^the reply act was performed$")
    fun performed() {
        checkNotNull(world.outcome).getOrThrow()
    }

    @Then("""^the reply act is refused as not offered "([^"]+)"$""")
    fun notOffered(curie: String) = assertEquals(
        world.resource.layers.type.profile.reference(curie.substringAfter(':')),
        world.refused<NotOfferedException>().rel,
    )

    @Then("""^the reply act is refused as not offered at "([^"]+)"$""")
    fun notOfferedAt(relation: String) = assertEquals(relation, world.refused<NotOfferedException>().rel)

    @Then("""^the reply act is refused as invalid at "([^"]+)"$""")
    fun invalid(pointer: String) = assertEquals(
        listOf(pointer),
        world.refused<UnprocessableContentException>().violations.map { it.pointer }.distinct(),
    )

    /** Sends [act] on the reply [reference] with [validator] (none, stale or current), and keeps the outcome. */
    private fun sendAct(reference: String, act: String, body: String, validator: String = "current") {
        val sent = when (validator) {
            "none" -> null
            "stale" -> "v0"
            else -> "v${checkNotNull(world.store.get(reference)).version}"
        }
        world.outcome = runCatching {
            runBlocking {
                val current = { checkNotNull(world.store.get(reference)) }
                val checked = world.pipeline.check(act, sent, current, body = body) { reply, v ->
                    v == "v${reply.version}"
                }
                world.pipeline.run(act, body, checked) { input -> world.resource.perform(checked, act, input) }
            }
        }
    }

    private fun typeOf(body: String) = if ("\"failed\"" in body) Replies.PROBLEM else Act.MERGE_PATCH
}
