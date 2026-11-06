package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartSettings
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Path
import java.security.MessageDigest

/** The gateway in front of a door upstream, reading it with one connector, all in memory. */
class ConnectorSteps {
    private lateinit var upstream: DoorUpstream
    private var connector: String? = null
    private var started: Result<HttpHandler>? = null
    private val responses = mutableListOf<Response>()
    private val mapper = ObjectMapper()

    private val gateway: HttpHandler get() = (started ?: start().also { started = it }).getOrThrow()
    private val json: JsonNode get() = mapper.readTree(responses.last().bodyString())

    @Given("the door upstream signals by {}, read by the connector {string}")
    fun upstream(signal: String, name: String) {
        upstream = DoorUpstream(Signal.of(signal))
        connector = name
    }

    @Given("the door upstream signals by {}, with no connector")
    fun upstreamAlone(signal: String) {
        upstream = DoorUpstream(Signal.of(signal))
        connector = null
    }

    @Given("the door upstream's description names the operation {string} as the relation {string}")
    fun relation(operation: String, relation: String) {
        upstream.relations[operation] = relation
    }

    @Given("door {long} is a vault door, {string}")
    fun vault(id: Long, status: String) {
        upstream.doors[id] = status
        upstream.vaults += id
    }

    @Given("door {long} is {string}")
    fun door(id: Long, status: String) {
        upstream.doors[id] = status
    }

    @When("the gateway starts")
    fun starts() {
        started = start()
    }

    @When("a client gets door {long} through the gateway")
    fun get(id: Long) {
        responses += gateway(signed(Request(Method.GET, "/doors/$id")))
    }

    @When("the client sends {string} with the ETag it holds")
    fun send(key: String) {
        val etag = checkNotNull(responses.last().header("ETag"))
        val target = json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        responses += gateway(
            signed(Request(Method.POST, target)).header("If-Match", etag)
                .header("Content-Type", "application/json").body("{}"),
        )
    }

    @Then("the response status is {int}")
    fun status(code: Int) = assertEquals(code, responses.last().status.code, responses.last().bodyString())

    @Then("the gateway offers")
    fun offers(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("act") }.toSet(),
        json.path("_forms").fieldNames().asSequence().toSet(),
        "$json",
    )

    @Then("the door's state has no member {string}")
    fun hidden(name: String) = assertFalse(json.has(name), "$json")

    @Then("the gateway says {string} is absent because {string}")
    fun absent(key: String, reason: String) {
        val href = json.path("_links").path("hr:unavailable").path("href").asText()
        val explanation = mapper.readTree(gateway(signed(Request(Method.GET, href))).bodyString())
        val reasons = explanation.path("unavailable")
            .associate { it.path("rel").asText() to it.path("reason").asText() }
        assertEquals(reason, reasons["/profiles/doors#${key.substringAfter(':')}"], "$reasons")
    }

    @Then("the gateway refuses to start, saying {string}")
    fun refuses(says: String) = assertEquals(says, started?.exceptionOrNull()?.message)

    private fun start(): Result<HttpHandler> = runCatching {
        val spec = GatewaySpec(upstream.handler(Request(Method.GET, "/openapi.json")).bodyString())
        val loaded = connector?.let { name ->
            Connector.load(Path.of(checkNotNull(javaClass.getResource("/connectors/$name")).toURI()), mapper)
        }
        Gateway(spec, upstream.handler, loaded?.profile ?: "doors", connector = loaded)
            .handler(Credentials.parse(users = "", tokens = sha256(TOKEN)))
    }

    private fun signed(request: Request): Request =
        request.header("Authorization", "Bearer $TOKEN").header("Accept", "application/hal+json")

    private companion object {
        const val TOKEN = "door-token"

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
