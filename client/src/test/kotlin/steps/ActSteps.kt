package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActSteps(private val ctx: ClientContext) {

    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val jsonMapper = ObjectMapper()

    @Given("the service returns the following on enter:")
    fun givenEnterResponse(body: String) {
        ctx.enterBody = bodyToString(body)
    }

    @And("acting on {string} returns:")
    fun givenActResponse(@Suppress("UNUSED_PARAMETER") rel: String, body: String) {
        ctx.actBody = bodyToString(body)
    }

    @When("the client enters the service")
    fun whenClientEnters() {
        ctx.enqueueEnterResponse()
        ctx.current = ctx.client.enter("https://example.com/")
    }

    @And("the client acts on {string} with:")
    fun whenClientActs(rel: String, body: String) {
        ctx.enqueueActResponse()
        val payload = createPayload(body)
        ctx.current = ctx.client.act(rel, payload)
    }

    @When("the client attempts to act on {string} with:")
    fun whenClientAttemptsAct(rel: String, body: String) {
        val payload = createPayload(body)
        ctx.lastError = runCatching { ctx.client.act(rel, payload) }.exceptionOrNull()
    }

    @Then("the client raises an error")
    fun thenClientRaisesError() {
        assertNotNull(ctx.lastError, "Expected an error but none was raised")
    }

    @Then("no request was submitted to the service")
    fun thenNoRequestSubmitted() {
        assertTrue(ctx.transport.submits.isEmpty(), "Expected no submit, but got ${ctx.transport.submits}")
    }

    @Then("the manifest affordances are:")
    fun thenManifestAffordances(entries: List<ManifestEntry>) {
        entries.forEach { (rel, present) ->
            if (present)
                assertTrue(ctx.current.containsAffordance(rel), "Expected '$rel' in manifest")
            else
                assertFalse(ctx.current.containsAffordance(rel), "Expected '$rel' absent from manifest")
        }
    }

    private fun createPayload(body: String): JsonObject = Json.parseToJsonElement(
        bodyToString(body)
    ).jsonObject

    private fun bodyToString(body: String): String = jsonMapper.writeValueAsString(yamlMapper.readTree(body))
}
