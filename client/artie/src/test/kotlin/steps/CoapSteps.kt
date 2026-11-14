package steps

import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Steps for SPEC §6.0.1: ARTIE's CoAP binding against a scripted CoAP server, over UDP. */
class CoapSteps(private val ctx: ClientContext) {

    private var server: FakeCoapServer? = null
    private val coap: FakeCoapServer get() = checkNotNull(server) { "no CoAP service in this scenario" }
    private var validatorBeforeAct: ByteArray? = null

    @After
    fun stop() {
        server?.stop()
    }

    @Given("a CoAP service holds bed {int} {word}")
    fun service(@Suppress("UNUSED_PARAMETER") bed: Int, status: String) {
        server = FakeCoapServer(status)
    }

    @Given("the CoAP service answers at rung {word}")
    fun answersAt(rung: String) {
        coap.rung = when (rung) {
            "L1" -> "forms=link"
            "L2" -> "forms=link, schema=link"
            else -> null
        }
    }

    @Given("the CoAP service will bring bed {int} into being on the next act")
    fun createsNext(bed: Int) {
        coap.createsNext = "beds/$bed"
    }

    @Given("the CoAP service will refuse the next act with {word} and problem type {string}")
    fun refuseNext(code: String, type: String) {
        val (codeClass, detail) = code.split('.').map { it.toInt() }
        coap.refuseNext(ResponseCode.valueOf(codeClass shl CLASS_SHIFT or detail), type)
    }

    @When("the client enters the CoAP bed")
    fun enter() {
        ctx.current = ctx.client.enter(coap.url)
        validatorBeforeAct = coap.validator
    }

    @Then("the CoAP request had Accept {int}")
    fun accept(format: Int) = assertEquals(format, coap.requests.last().options.accept)

    @Then("the CoAP request had option {int} {string}")
    fun option(number: Int, value: String) {
        val values = coap.requests.last().options.others.filter { it.number == number }.map { it.stringValue }
        assertTrue(value in values, "option $number: $values")
    }

    @Then("the CoAP request had option {int}")
    fun hasOption(number: Int) =
        assertTrue(coap.requests.last().options.others.any { it.number == number }, "no option $number")

    @Then("every CoAP request had option {int} with a W3C traceparent")
    fun everyTraced(number: Int) {
        val parents = coap.requests.map { request -> request.options.others.singleOrNull { it.number == number } }
        val wellFormed = parents.isNotEmpty() && parents.all { it != null && TRACEPARENT.matches(it.stringValue) }
        assertTrue(wellFormed, "traceparents: $parents")
    }

    @Then("the CoAP requests share a trace id but not a span id")
    fun shareTrace() {
        val parts = coap.requests.map { request ->
            val value = request.options.others.single { it.number == FakeCoapServer.TRACEPARENT.number }.stringValue
            value.split('-').let { it[1] to it[2] }
        }
        assertEquals(1, parts.map { it.first }.distinct().size, "$parts")
        assertEquals(parts.size, parts.map { it.second }.distinct().size, "$parts")
    }

    @Then("the {string} event names the span of the CoAP act")
    fun eventNamesSpan(event: String) {
        val act = coap.requests.first { it.code.name == "POST" }
        val span = act.options.others.single { it.number == FakeCoapServer.TRACEPARENT.number }.stringValue.split('-')[2]
        val lines = (ctx.eventSink as MemoryEventSink).lines.map { Json.parseToJsonElement(it).jsonObject }
        val spans = lines.filter { it["event_name"]?.jsonPrimitive?.content == event }
            .map { it["span_id"]?.jsonPrimitive?.content }
        assertEquals(listOf(span), spans)
    }

    @Then("the CoAP acts were")
    fun actsWere(table: io.cucumber.datatable.DataTable) = assertEquals(
        table.asMaps().map { it.getValue("code") to it.getValue("path") },
        coap.requests.filter { it.code.name != "GET" }.map { it.code.name to it.options.uriPathString },
    )

    @Then("the client holds the CoAP bed's validator")
    fun holdsValidator() = assertEquals(hex(coap.validator), ctx.client.current?.etag)

    @Then("the CoAP act carried If-Match with the bed's validator")
    fun actCarried() {
        val act = coap.requests.first { it.code.name == "POST" }
        assertContentEquals(checkNotNull(validatorBeforeAct), act.options.ifMatch.single())
    }

    private companion object {
        const val CLASS_SHIFT = 5
        val TRACEPARENT = Regex("00-[0-9a-f]{32}-[0-9a-f]{16}-01")
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
