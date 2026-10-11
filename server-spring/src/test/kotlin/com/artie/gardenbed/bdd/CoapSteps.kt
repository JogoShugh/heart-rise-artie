package com.artie.gardenbed.bdd

import com.artie.heart.coap.CoapFrontDoor
import com.artie.heart.coap.CoapOptions
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.coap.Option
import org.eclipse.californium.core.coap.Request
import org.eclipse.californium.core.coap.Response
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.core.network.CoapEndpoint
import org.eclipse.californium.elements.config.Configuration
import org.eclipse.californium.elements.config.UdpConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.MessageDigest
import java.util.Base64

/** Steps for SPEC §6.0.1: the server over CoAP, asked by a real CoAP client on UDP. */
class CoapSteps(
    private val door: CoapFrontDoor,
    private val http: HttpWorld,
    private val store: BedEventLog,
) {
    private val client: CoapClient by lazy {
        CoapConfig.register()
        UdpConfig.register()
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(Configuration.createStandardWithoutFile())
            .setOptionRegistry(CoapOptions.registry)
            .build()
        CoapClient().setEndpoint(endpoint).setTimeout(TIMEOUT_MS)
    }
    private lateinit var last: Response
    private var held: ByteArray? = null
    private var prefer: String = ""
    private var linkIntegrity: String? = null

    @After
    fun shutdown() {
        if (this::last.isInitialized) client.shutdown()
    }

    @Given("a CoAP client got {string} with Prefer {string}")
    @When("a CoAP client gets {string} with Prefer {string}")
    fun get(path: String, prefer: String) {
        this.prefer = prefer
        send(read(path))
        held = last.options.eTags.firstOrNull()
    }

    @When("the CoAP client gets {string} again with the ETag it holds")
    fun revalidate(path: String) = send(read(path).apply { options.addETag(checkNotNull(held)) })

    @When("the CoAP client follows the representation's {string} link")
    fun follow(rel: String) {
        val link = body().path("_links").path(rel)
        linkIntegrity = link.path("integrity").asText()
        send(Request.newGet().apply { setURI(uri(link.path("href").asText())) })
    }

    @When("a CoAP client asks for HAL-FORMS at {string}")
    fun askHalForms(path: String) =
        send(read(path).apply { options.addOption(Option(CoapOptions.ACCEPT_PROFILE, "<$HAL_FORMS_PROFILE>")) })

    /** [ifMatch] is "no If-Match", "If-Match \"<text>\"" or "the ETag it holds". */
    @When("the CoAP client posts {} to {string} with {}")
    fun post(body: String, path: String, ifMatch: String) {
        val request = Request.newPost().apply {
            setURI(uri(path))
            setPayload(body)
            options.contentFormat = CoapOptions.JSON
            if (prefer.isNotEmpty()) options.addOption(Option(CoapOptions.PREFER, prefer))
        }
        when {
            ifMatch == "the ETag it holds" -> request.options.addIfMatch(checkNotNull(held))
            ifMatch.startsWith("If-Match ") ->
                request.options.addIfMatch(ifMatch.removePrefix("If-Match ").trim('"').toByteArray())
        }
        send(request)
    }

    @Then("the CoAP response is {word}")
    fun code(expected: String) {
        val code = last.code
        assertEquals(expected, "%d.%02d".format(code.codeClass, code.codeDetail), String(last.payload ?: ByteArray(0)))
    }

    @Then("the CoAP Content-Format is {int}")
    fun contentFormat(expected: Int) = assertEquals(expected, last.options.contentFormat)

    @Then("the CoAP option {int} is {string}")
    fun option(number: Int, expected: String) {
        val values = last.options.others.filter { it.number == number }.map { it.stringValue }
        assertTrue(expected in values, "option $number: $values")
    }

    @Then("the CoAP Max-Age is {long}")
    fun maxAge(expected: Long) = assertEquals(expected, last.options.maxAge)

    @Then("the CoAP ETag is the first 8 bytes of SHA-256 over {string}")
    fun etag(httpETag: String) {
        val expected = MessageDigest.getInstance("SHA-256").digest(httpETag.toByteArray()).copyOf(ETAG_BYTES)
        assertArrayEquals(expected, last.options.eTags.single())
    }

    @Then("the CoAP body is the same JSON as the HTTP body")
    fun sameAsHttp() = assertEquals(http.last.json, body())

    @Then("the CoAP body matches the integrity of the link that led to it")
    fun matchesIntegrity() {
        val digest = MessageDigest.getInstance("SHA-256").digest(last.payload)
        assertEquals(linkIntegrity, "sha256-" + Base64.getEncoder().encodeToString(digest))
    }

    @Then("the CoAP problem type is {string}")
    fun problemType(type: String) = assertEquals(PROBLEMS + type, body().path("type").asText())

    @Then("the CoAP body links {word} to {string}")
    fun links(rel: String, href: String) {
        val link = body().path("_links").path(rel)
        val hrefs = if (link.isArray) link.map { it.path("href").asText() } else listOf(link.path("href").asText())
        assertTrue(href in hrefs, "$rel links: $hrefs")
    }

    @Then("bed {long} is at state version {int}")
    fun stateVersion(id: Long, version: Int) = runBlocking {
        assertEquals(version, store.version(id))
    }

    /** A Layer 1 read: it says No-Cache, and asks for the rung in Prefer. */
    private fun read(path: String) = Request.newGet().apply {
        setURI(uri(path))
        options.addOption(Option(CoapOptions.NO_CACHE))
        if (prefer.isNotEmpty()) options.addOption(Option(CoapOptions.PREFER, prefer))
    }

    private fun send(request: Request) {
        last = checkNotNull(client.advanced(request)) { "no CoAP response to ${request.uri}" }.advanced()
    }

    private fun body(): JsonNode = ObjectMapper().readTree(last.payload)

    private fun uri(path: String) = "coap://localhost:${door.port}$path"

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val ETAG_BYTES = 8
        const val PROBLEMS = "https://github.com/jogoshugh/heart-rise/problems/"
        const val HAL_FORMS_PROFILE = "https://github.com/jogoshugh/heart-rise/hal-forms"
    }
}
