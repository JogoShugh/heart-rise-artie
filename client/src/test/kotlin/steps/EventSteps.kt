package steps

import heart.EventSink
import heart.EventSinks
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Keeps every event line in memory, for scenarios to read. */
class MemoryEventSink : EventSink {
    val lines = mutableListOf<String>()
    override fun write(line: String) {
        lines += line
    }
}

/** Steps for ARTIE's trace context and event log (hack log, 2026-10-10). */
class EventSteps(private val ctx: ClientContext) {
    private var eventFile: Path? = null

    @Given("the client keeps an event log")
    fun keepLog() {
        ctx.eventSink = MemoryEventSink()
    }

    @Given("the client writes events to a file and posts them to {string}")
    fun fileAndPost(url: String) {
        val file = Files.createTempFile("artie-events", ".jsonl").also { Files.delete(it) }
        eventFile = file
        ctx.eventSink = EventSinks.fromEnvironment(
            mapOf("ARTIE_EVENTS" to file.toString(), "ARTIE_EVENTS_URL" to url),
            ctx.transport,
        )
    }

    @Given("the client writes events to a file only when ARTIE_EVENTS is set, and it is not")
    fun fileOnlyWhenSet() {
        eventFile = Files.createTempFile("artie-events", ".jsonl").also { Files.delete(it) }
        ctx.eventSink = EventSinks.fromEnvironment(emptyMap(), ctx.transport)
    }

    @When("the client is done")
    fun done() = ctx.client.done()

    /** Every client says it is done when its scenario ends, so a watcher such as Buster can close its windows. */
    @After
    fun clientsDone() = ctx.created.forEach { it.done() }

    @Then("every request on the wire had a W3C traceparent")
    fun everyTraced() {
        val parents = ctx.transport.requests.map { it.header("traceparent") }
        val wellFormed = parents.isNotEmpty() && parents.all { it != null && TRACEPARENT.matches(it) }
        assertTrue(wellFormed, "traceparents: $parents")
    }

    @Then("the first two requests share a trace id but not a span id")
    fun firstTwoShareTrace() {
        val (first, second) = ctx.transport.requests.take(2).map { parts(it.header("traceparent")!!) }
        assertEquals(first.first, second.first)
        assertNotEquals(first.second, second.second)
    }

    @Then("the third request has another trace id")
    fun thirdOtherTrace() {
        val traces = ctx.transport.requests.take(3).map { parts(it.header("traceparent")!!).first }
        assertNotEquals(traces[0], traces[2])
    }

    @Then("the event log has {string} with {string} {string} for the request to {string}")
    fun logHas(event: String, attribute: String, value: String, url: String) {
        val request = ctx.transport.requests.last { it.url == url }
        val (trace, span) = parts(request.header("traceparent")!!)
        val match = events().any {
            it.text("event_name") == event && it.text("trace_id") == trace && it.text("span_id") == span &&
                it["attributes"]?.jsonObject?.get(attribute)?.jsonPrimitive?.content == value
        }
        assertTrue(match, "no $event with $attribute=$value for span $span in ${memory().lines}")
    }

    @Then("every event line has the fields {string}")
    fun fields(names: String) {
        val expected = names.split(',').map { it.trim() }.toSet()
        assertTrue(events().isNotEmpty())
        events().forEach { assertEquals(expected, it.keys, "fields of $it") }
    }

    @Then("the last event is {string}")
    fun lastEvent(event: String) = assertEquals(event, events().last().text("event_name"))

    @Then("the event file has a {string} line")
    fun fileHas(event: String) {
        val lines = Files.readAllLines(eventFile!!)
        assertTrue(lines.any { Json.parseToJsonElement(it).jsonObject.text("event_name") == event }, "file: $lines")
    }

    @Then("the client posted a {string} line to {string}")
    fun posted(event: String, url: String) {
        val bodies = ctx.transport.requests.filter { it.method == "POST" && it.url == url }.mapNotNull { it.body }
        assertTrue(bodies.any { Json.parseToJsonElement(it).jsonObject.text("event_name") == event }, "posts: $bodies")
    }

    @Then("no event file was written")
    fun noFile() = assertFalse(Files.exists(eventFile!!), "unexpected $eventFile")

    private fun memory(): MemoryEventSink = ctx.eventSink as MemoryEventSink

    private fun events(): List<JsonObject> = memory().lines.map { Json.parseToJsonElement(it).jsonObject }

    private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.content

    /** traceparent is version-traceid-spanid-flags (W3C Trace Context §3.2). */
    private fun parts(traceparent: String): Pair<String, String> =
        traceparent.split('-').let { it[1] to it[2] }

    private companion object {
        val TRACEPARENT = Regex("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$")
    }
}
