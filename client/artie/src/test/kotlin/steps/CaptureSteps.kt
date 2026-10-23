package steps

import heart.HeartRequest
import heart.script.Run
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** script-capture.feature: what a run's trace records of the form or link each step used. */
class CaptureSteps(private val ctx: ClientContext) {

    @Then("the run's step {int} used")
    fun used(step: Int, table: DataTable) {
        val chose = step(step)["chose"]?.jsonObject
        val rows = table.asMaps().associate { it.getValue("what") to it.getValue("value") }
        assertEquals(rows, rows.keys.associateWith { chose?.get(it)?.jsonPrimitive?.content })
    }

    @Then(
        "the run's step {int} guards on rung L2, and on the set, form and schema the gateway serves, " +
            "with its If-Match",
    )
    fun guards(step: Int) {
        val traced = step(step)
        val guard = checkNotNull(traced["guard"]?.jsonObject) { "no guard in $traced" }
        assertEquals("L2", guard["rung"]?.jsonPrimitive?.content)
        val set = guard.getValue("set").jsonObject
        val setBody = fetch(set.getValue("href").jsonPrimitive.content)
        assertEquals(set["integrity"]?.jsonPrimitive?.content, integrity(setBody), "the set as the gateway serves it")
        val relation = traced.getValue("chose").jsonObject.getValue("relation").jsonPrimitive.content
        val forms = Json.parseToJsonElement(setBody).jsonObject.getValue("_forms").jsonObject
        val form = forms.getValue(relation).jsonObject
        assertEquals(form["hr:integrity"]?.jsonPrimitive?.content, guard["form"]?.jsonPrimitive?.content)
        val schema = guard.getValue("schema").jsonObject
        val ref = form.getValue("schema").jsonObject
        assertTrue(schema.getValue("href").jsonPrimitive.content.endsWith(ref.getValue("\$ref").jsonPrimitive.content))
        assertEquals(ref["hr:integrity"]?.jsonPrimitive?.content, schema["integrity"]?.jsonPrimitive?.content)
        assertTrue(guard["if-match"]?.jsonPrimitive?.content.orEmpty().contains(".L2."), "If-Match: $guard")
    }

    /** The trace records the If-Match that went on the wire, and none for a blind write. */
    @Then("the run's step {int} records the comment sent with If-Match")
    fun recordsIfMatch(step: Int) = assertTrue(ifMatch(step) != null, "${step(step)}")

    @Then("the run's step {int} records the comment sent with no If-Match")
    fun recordsNoIfMatch(step: Int) = assertEquals(null, ifMatch(step), "${step(step)}")

    private fun ifMatch(step: Int): String? = step(step)["guard"]?.jsonObject?.get("if-match")?.jsonPrimitive?.content

    @Then("the run's step {int} kept one call to Claude, asked with {string} and answered with {string}")
    fun keptCall(step: Int, intent: String, chose: String) {
        val calls = (traced()[step - 1]["model"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        assertEquals(1, calls.size, "model calls: $calls")
        val call = calls.single().jsonObject
        assertEquals(heart.ClaudeReasoner.DEFAULT_MODEL, call["model"]?.jsonPrimitive?.content)
        assertTrue(intent in call.getValue("asked").jsonPrimitive.content, "asked: $call")
        assertTrue(chose in call.getValue("answered").jsonPrimitive.content, "answered: $call")
    }

    @Then("the run's step {int} sends its values typed as the form's schema says")
    fun typed(step: Int, table: DataTable) {
        val values = step(step)["values"]?.jsonObject
        val read = table.asMaps().map { row ->
            mapOf("field" to row.getValue("field"),
                "type" to values?.get(row.getValue("field"))?.jsonObject?.get("type")?.jsonPrimitive?.content)
        }
        assertEquals(table.asMaps(), read)
    }

    @io.cucumber.java.en.Given("ARTIE keeps its exchanges")
    fun keepsExchanges() {
        ctx.keepExchanges = true
    }

    @Then("the run's step {int} kept the exchange {string} to {string}, answered {int}")
    fun keptExchange(step: Int, method: String, url: String, status: Int) {
        val kept = exchanges(traced()[step - 1]).map { exchange ->
            val request = exchange.getValue("request").jsonObject
            Triple(request.getValue("method").jsonPrimitive.content, request.getValue("url").jsonPrimitive.content,
                exchange.getValue("response").jsonObject.getValue("status").jsonPrimitive.content.toInt())
        }
        assertTrue(Triple(method, url, status) in kept, "kept: $kept")
    }

    @Then("the run kept these exchanges")
    fun keptInOrder(table: DataTable) = assertEquals(
        table.asMaps().map { listOf(it["step"], it["method"], it["url"], it["status"]) },
        traced().flatMapIndexed { i, step ->
            exchanges(step).map { exchange ->
                val request = exchange.getValue("request").jsonObject
                listOf("${i + 1}", request.getValue("method").jsonPrimitive.content,
                    request.getValue("url").jsonPrimitive.content,
                    exchange.getValue("response").jsonObject.getValue("status").jsonPrimitive.content)
            }
        },
    )

    @Then("no exchange the run kept sends {string} or {string}")
    fun sendsNeither(first: String, second: String) = traced().flatMap(::exchanges).forEach { exchange ->
        val headers = exchange.getValue("request").jsonObject.getValue("headers").jsonObject.keys
        assertTrue(headers.none { it.equals(first, true) || it.equals(second, true) }, "$headers")
    }

    @Then("every exchange the run kept sends its credential as {string} and a traceparent")
    fun credentialAsToken(token: String) {
        val all = traced().flatMap(::exchanges)
        assertTrue(all.isNotEmpty(), "no exchanges kept")
        all.forEach { exchange ->
            val headers = exchange.getValue("request").jsonObject.getValue("headers").jsonObject
            assertEquals(token, headers["Authorization"]?.jsonPrimitive?.content, "$headers")
            assertTrue(headers["traceparent"]?.jsonPrimitive?.content.orEmpty().startsWith("00-"), "$headers")
        }
    }

    private fun exchanges(step: JsonObject): List<JsonObject> =
        (step["exchanges"] as? kotlinx.serialization.json.JsonArray).orEmpty().map { it.jsonObject }

    @Then("the run's trace never holds the key {string}")
    fun noKey(key: String) = assertTrue(key !in Run.current.traceJson(), "the key is in the trace")

    private fun traced(): List<JsonObject> {
        val steps = Json.parseToJsonElement(Run.current.traceJson()).jsonObject.getValue("steps")
        return (steps as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
    }

    private fun step(step: Int): JsonObject = Run.current.trace[step - 1].json()

    /** A body the gateway serves at [href], as it serves it, read past ARTIE's cache. */
    private fun fetch(href: String): String {
        val url = if (href.startsWith("http")) href else "https://github.gateway.test$href"
        val headers = mapOf("Authorization" to "Bearer ada-token", "Accept" to "application/hal+json")
        return checkNotNull(ctx.inProcess).send(HeartRequest("GET", url, headers)).body
    }

    private fun integrity(body: String): String =
        "sha256-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body.toByteArray()))
}
