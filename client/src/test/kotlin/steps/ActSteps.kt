package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.PROPERTIES
import heart.REQUIRED
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Steps for entering a service, reading what came back, and acting on it. */
class ActSteps(private val ctx: ClientContext) {

    // Cucumber matches step text, not the Given/When keyword: one annotation serves both.
    @When("the client enters {string}")
    fun enter(url: String) {
        ctx.current = ctx.client.enter(ctx.url(url))
        ctx.requestsAfterEnter = ctx.transport.requests.size
    }

    @When("the client tries to enter {string}")
    fun tryEnter(url: String) {
        ctx.lastError = runCatching { ctx.client.enter(ctx.url(url)) }.exceptionOrNull()
    }

    @When("the client acts on {string} with:")
    fun act(rel: String, yaml: String) {
        ctx.current = ctx.client.act(rel, payload(yaml))
    }

    @When("the client tries to act on {string} with:")
    fun tryAct(rel: String, yaml: String) {
        ctx.lastError = runCatching { ctx.client.act(rel, payload(yaml)) }.exceptionOrNull()
    }

    @Then("the choices of {string} for {string}, with {}, are {string}")
    fun choices(rel: String, property: String, fields: String, expected: String) {
        val choices = ctx.client.choices(rel)?.of(Json.parseToJsonElement(fields).jsonObject) ?: error("no form $rel")
        val shown = choices[property].orEmpty().joinToString(", ") { "${it.value.jsonPrimitive.content} ${it.prompt}" }
        assertEquals(expected, shown)
    }

    @Then("the client raised an error mentioning {string}")
    fun raisedError(text: String) {
        val error = assertNotNull(ctx.lastError, "expected an error, none was raised")
        assertTrue(error.message.orEmpty().contains(text), "error was: ${error.message}")
    }

    @Then("the resource state contains:")
    fun stateContains(entries: List<StateEntry>) = entries.forEach { (key, expected) ->
        assertEquals(expected, ctx.current.state[key]?.jsonPrimitive?.content, "state[$key]")
    }

    @Then("the resource state does not contain the key {string}")
    fun stateLacks(key: String) = assertFalse(ctx.current.state.containsKey(key), "state has $key")

    @Then("the manifest offers exactly {string}")
    fun manifestOffers(rels: String) =
        assertEquals(rels.split(',').map { it.trim() }.toSet(), ctx.current.affordances.keys)

    @Then("the {string} affordance is {word} to {string}")
    fun affordanceTarget(rel: String, method: String, url: String) {
        val form = assertNotNull(ctx.current.affordance(rel), "no $rel")
        assertEquals(method, form.method)
        assertEquals(url, form.target)
    }

    @Then("the {string} field in the {string} schema has the {string} keyword")
    fun schemaKeyword(property: String, rel: String, keyword: String) {
        val props = ctx.current.affordance(rel)?.schema?.get(PROPERTIES)?.jsonObject ?: error("no $rel properties")
        assertTrue(props[property]?.jsonObject?.containsKey(keyword) == true, "$property lacks $keyword")
    }

    @Then("the {string} schema marks {string} as required")
    fun schemaRequired(rel: String, field: String) {
        val required = ctx.current.affordance(rel)?.schema?.get(REQUIRED)?.jsonArray?.map { it.jsonPrimitive.content }
        assertTrue(required.orEmpty().contains(field), "required: $required")
    }

    /** Reads the client's own view, which a refusal's re-sync may have replaced. */
    @Then("the client holds the ETag {string}")
    fun holdsETag(etag: String) = assertEquals("\"$etag\"", ctx.client.current?.etag)

    @Then("the client's current manifest is empty")
    fun manifestEmpty() = assertTrue(ctx.client.current?.affordances.orEmpty().isEmpty())

    @Then("the client raised a {string} refusal")
    fun raisedRefusal(kind: String) {
        val error = assertNotNull(ctx.lastError, "expected a refusal, none was raised")
        assertEquals(kind, error::class.simpleName, "raised: $error")
    }

    private fun payload(yaml: String): JsonObject =
        Json.parseToJsonElement(jsonMapper.writeValueAsString(yamlMapper.readTree(yaml))).jsonObject

    private companion object {
        val yamlMapper = ObjectMapper(YAMLFactory())
        val jsonMapper = ObjectMapper()
    }
}
