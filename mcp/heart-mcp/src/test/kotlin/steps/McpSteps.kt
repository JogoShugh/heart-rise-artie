package steps

import heart.EventSinks
import heart.mcp.HeartMcp
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class McpSteps(private val host: McpHost) {

    /** Each @live scenario starts from the garden's seed data. */
    @Before("@live")
    fun reset(scenario: Scenario) {
        val script = System.getenv("ARTIE_RESET") ?: return
        val process = ProcessBuilder(script).redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        val exit = process.waitFor()
        check(exit == 0) { "$script exited $exit before '${scenario.name}'" }
    }

    @Given("an MCP host connected to heart-mcp with the bookmark {string}")
    fun connected(service: String) = host.connect(service)

    @Then("the host sees exactly the tools {string}")
    fun seesTools(names: String) = assertEquals(list(names), host.tools().map { it.name })

    @Then("no tool takes an input named like an address, a method or a header")
    fun noAddressInputs() {
        val inputs = host.tools().flatMap { it.inputSchema.properties?.keys.orEmpty() }
        val banned = Regex("(?i)url|uri|href|address|method|header|etag|if-?match|validator")
        assertTrue(inputs.none { banned.containsMatchIn(it) }, "tool inputs: $inputs")
    }

    @When("the host calls heart_look with the service {string}")
    fun look(service: String) {
        host.call("heart_look", buildJsonObject { put("service", service) })
    }

    @Then("the tool answers with an error mentioning {string}")
    fun errorMentioning(text: String) {
        val result = assertNotNull(host.last)
        assertEquals(true, result.isError, "expected an error, got ${text(result)}")
        assertTrue(text in text(result), "error was: ${text(result)}")
    }

    @Given("the host has found the bed {string}")
    fun hasFoundBed(label: String) = findBed(label)

    @When("the host finds the bed {string} from the garden's bookmark")
    fun findBed(label: String) {
        look("garden")
        act("garden:search-beds", """{"name": "$label"}""")
        ok()
        host.call("heart_follow", buildJsonObject { put("rel", "item"); put("title", label) })
        ok()
    }

    @When("the host calls heart_act {string} with {}")
    fun act(act: String, fields: String) {
        host.call("heart_act", buildJsonObject { put("act", act); put("fields", Json.parseToJsonElement(fields)) })
    }

    @When("the host calls heart_explain {string}")
    fun explain(act: String) {
        host.call("heart_explain", buildJsonObject { put("act", act) })
    }

    @When("the host calls heart_describe {string}")
    fun describe(act: String) {
        host.call("heart_describe", buildJsonObject { put("act", act) })
    }

    @When("the host calls heart_back")
    fun back() {
        host.call("heart_back", JsonObject(emptyMap()))
    }

    @Given("another client waters bed {int} with {int} ml")
    fun anotherClientWaters(bed: Int, milliliters: Int) {
        val other = HeartMcp.client("another client", EventSinks.NONE)
        other.enter("${host.baseUrl}/beds/$bed")
        other.act("garden:water-cell", buildJsonObject { put("milliliters", milliliters) })
    }

    @Then("the result offers exactly the acts {string}")
    fun offersExactly(names: String) =
        assertEquals(list(names).sorted(), ok()["acts"]!!.jsonArray.map { it.jsonObject.string("name") }.sorted())

    @Then("every offered act has a title and a schema")
    fun actsDescribed() = ok()["acts"]!!.jsonArray.forEach { act ->
        assertTrue(act.jsonObject["title"] is JsonPrimitive, "no title: $act")
        assertTrue(act.jsonObject["schema"] is JsonObject, "no schema: $act")
    }

    @Then("no address appears anywhere in the result")
    fun noAddress() {
        val found = addresses(ok())
        assertTrue(found.isEmpty(), "addresses in the result: $found")
    }

    @Then("the result's state has {string} {string}")
    fun stateHas(name: String, value: String) = assertEquals(value, state()[name]?.jsonPrimitive?.content)

    @Then("the result's state has {string} {int}")
    fun stateHasNumber(name: String, value: Int) = assertEquals(value, state()[name]?.jsonPrimitive?.int)

    @Then("the outcome is {string}")
    fun outcomeIs(kind: String) = assertEquals(kind, outcome().string("kind"), "outcome: ${outcome()}")

    @Then("the outcome mentions {string}")
    fun outcomeMentions(text: String) = assertTrue(text in outcome().toString(), "outcome: ${outcome()}")

    @Then("the event log shows {int} act(s) sent")
    fun actsSent(count: Int) =
        assertEquals(count, host.events.count { "\"event_name\":\"act.sent\"" in it }, host.events.joinToString("\n"))

    @Then("the explanation is {string}")
    fun explanationIs(reason: String) = assertEquals(reason, ok().string("explanation"))

    @Then("the description mentions {string}")
    fun descriptionMentions(text: String) =
        assertTrue(text in ok().string("description"), "description: ${ok()["description"]}")

    /** The last result's structured content; a tool error fails the step. */
    private fun ok(): JsonObject {
        val result = assertNotNull(host.last, "no tool was called")
        if (result.isError == true) fail("tool error: ${text(result)}")
        return assertNotNull(result.structuredContent, "no structured content: ${text(result)}")
    }

    private fun state(): JsonObject = ok()["resource"]!!.jsonObject["state"]!!.jsonObject

    private fun outcome(): JsonObject = assertNotNull(ok()["outcome"] as? JsonObject, "no outcome in ${ok()}")

    private fun text(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }

    /**
     * Keys and values that name an address: an href or target, a schema's own
     * id or reference, a path or URL of the service.
     */
    private fun addresses(element: JsonElement, path: String = ""): List<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            listOfNotNull(path.takeIf { key in ADDRESS_KEYS }?.let { "$it/$key" }) + addresses(value, "$path/$key")
        }
        is JsonArray -> element.flatMapIndexed { i, value -> addresses(value, "$path/$i") }
        is JsonPrimitive -> listOfNotNull(
            element.content
                .takeIf { element.isString && (it.startsWith("/") || host.baseUrl in it) }
                ?.let { "$path=$it" },
        )
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.content.orEmpty()

    private fun list(names: String) = names.split(",").map { it.trim() }

    private companion object {
        val ADDRESS_KEYS = setOf("href", "target", "\$id", "\$ref", "url", "method")
    }
}
