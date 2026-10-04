package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.HeartRequest
import heart.HeartResponse
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wire, as scenarios see it. A scripted response is written like an
 * HTTP response: a status line, header lines, a blank line, then the body
 * as YAML (sent to the client as JSON).
 */
class HttpSteps(private val ctx: ClientContext) {

    @Given("the service answers {word} {string} with:")
    fun serviceAnswers(method: String, url: String, raw: String) {
        ctx.transport.respond(method, url, parseResponse(raw))
    }

    @Then("the client sent {word} {string} with headers:")
    fun clientSent(method: String, url: String, headers: Map<String, String>) {
        val request = lastRequestTo(method, url)
        headers.forEach { (name, value) -> assertEquals(value, request.header(name), "$name on $method $url") }
    }

    @Then("that request had no {string} header")
    fun noHeader(name: String) = assertNull(ctx.transport.requests.last().header(name))

    @Then("that request had the JSON body {}")
    fun jsonBody(expected: String) {
        val sent = ctx.transport.requests.last().body ?: ""
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(sent))
    }

    @Then("the client sent no request after entering")
    fun noRequestAfterEnter() {
        val extra = ctx.transport.requests.drop(ctx.requestsAfterEnter)
        assertTrue(extra.isEmpty(), "unexpected requests: ${extra.map { "${it.method} ${it.url}" }}")
    }

    private fun lastRequestTo(method: String, url: String): HeartRequest =
        ctx.transport.requests.lastOrNull { it.method == method && it.url == url }
            ?: error("no $method $url among ${ctx.transport.requests.map { "${it.method} ${it.url}" }}")

    private fun parseResponse(raw: String): HeartResponse {
        val lines = raw.lines()
        val status = lines.first().substringBefore(' ').toInt()
        val blank = lines.indexOfFirst { it.isBlank() }.let { if (it < 0) lines.size else it }
        val headers = lines.subList(1, blank).associate { line ->
            line.substringBefore(':').trim() to listOf(line.substringAfter(':').trim())
        }
        val yaml = lines.drop(blank + 1).joinToString("\n")
        val body = if (yaml.isBlank()) "" else jsonMapper.writeValueAsString(yamlMapper.readTree(yaml))
        return HeartResponse(status, headers, body)
    }

    private companion object {
        val yamlMapper = ObjectMapper(YAMLFactory())
        val jsonMapper = ObjectMapper()
    }
}
