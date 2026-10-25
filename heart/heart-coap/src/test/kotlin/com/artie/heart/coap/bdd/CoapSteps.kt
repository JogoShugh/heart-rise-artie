package com.artie.heart.coap.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.JsonTrees
import com.artie.heart.coap.CoapFrontDoor
import com.artie.heart.coap.CoapOptions
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import com.artie.heart.coap.CoapsSettings
import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.scandium.DTLSConnector
import org.eclipse.californium.scandium.config.DtlsConfig
import org.eclipse.californium.scandium.config.DtlsConnectorConfig
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore
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

/** Steps for SPEC §6.0.1: the lamp over CoAP, asked by a real CoAP client on UDP. */
class CoapSteps(private val door: CoapFrontDoor, private val store: LampStore) {
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

    // ---- coaps: DTLS with a pre-shared key ----

    private var coapsAnswered: Boolean = false
    private var settings: Result<CoapsSettings?>? = null

    @When("a coaps client with identity {string} and its key gets {string}")
    fun coapsGet(identity: String, path: String) = coapsGet(identity, LAMP_KEY, path)

    @When("a coaps client with identity {string} and a wrong key gets {string}")
    fun coapsGetWrongKey(identity: String, path: String) = coapsGet(identity, "the-wrong-key-16b".toByteArray(), path)

    private fun coapsGet(identity: String, key: ByteArray, path: String) {
        DtlsConfig.register()
        CoapConfig.register()
        UdpConfig.register()
        val config = Configuration.createStandardWithoutFile()
        val dtls = DtlsConnectorConfig.builder(config)
            .set(DtlsConfig.DTLS_ROLE, DtlsConfig.DtlsRole.CLIENT_ONLY)
            .set(DtlsConfig.DTLS_CIPHER_SUITES, listOf(CipherSuite.TLS_PSK_WITH_AES_128_GCM_SHA256))
            .setAdvancedPskStore(AdvancedSinglePskStore(identity, key))
            .build()
        val endpoint = CoapEndpoint.Builder().setConfiguration(config).setConnector(DTLSConnector(dtls))
            .setOptionRegistry(CoapOptions.registry).build()
        val secure = CoapClient().setEndpoint(endpoint).setTimeout(COAPS_TIMEOUT_MS)
        try {
            val request = read("coaps://localhost:${door.securePort}$path", whole = true)
            val answer = secure.advanced(request)
            coapsAnswered = answer != null
            answer?.let { last = it.advanced() }
        } finally {
            secure.shutdown()
        }
    }

    @Then("the coaps request got no answer")
    fun noCoapsAnswer() = assertTrue(!coapsAnswered, "the server answered a client it should not admit")

    @Then("the CoAP lamp server's coaps endpoint listens on every address")
    fun coapsEverywhere() = assertTrue(door.secureAddress.address.isAnyLocalAddress, "listens on ${door.secureAddress}")

    @When("the coaps settings are port {int} and keys {string}")
    fun coapsSettings(port: Int, keys: String) {
        settings = runCatching { CoapsSettings.parse(port, keys) }
    }

    @Then("the coaps settings turn coaps off")
    fun coapsOff() = assertEquals(null, checkNotNull(settings).getOrThrow())

    @Then("the coaps settings admit {string}")
    fun coapsAdmit(identity: String) =
        assertTrue(identity in checkNotNull(checkNotNull(settings).getOrThrow()).keys.keys)

    @Then("the coaps settings fail with {string}")
    fun coapsFail(text: String) {
        val failure = checkNotNull(checkNotNull(settings).exceptionOrNull()) { "expected the settings to fail" }
        assertTrue(failure.message.orEmpty().contains(text), "failed with: ${failure.message}")
        assertTrue(!failure.message.orEmpty().contains("bGFtcC"), "the failure shows a key")
    }

    @Then("the CoAP lamp server listens on a loopback address only")
    fun loopbackOnly() = assertTrue(door.address.address.isLoopbackAddress, "listens on ${door.address}")

    @Given("lamp {long} is {string} at version {int} in the CoAP lamp server")
    fun lampIs(id: Long, status: String, version: Int) {
        store.lamps.clear()
        store.lamps[id] = Lamp(id, status, brightness = 50, version = version)
    }

    @Given("a CoAP client got {string} with Prefer {string}")
    @When("a CoAP client gets {string} with Prefer {string}")
    fun get(path: String, prefer: String) {
        this.prefer = prefer
        send(read(path))
        held = last.options.eTags.firstOrNull()
    }

    /** Reads [path] as the last read asked, keeping the lamp's validator the client holds. */
    @When("the CoAP client gets {string} as it got the lamp")
    fun getAsBefore(path: String) = send(read(path))

    @Then("the CoAP ETag is the lamp's")
    fun lampsETag() = assertArrayEquals(checkNotNull(held), last.options.eTags.singleOrNull())

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
    @When("^the CoAP client (posts|puts|patches|deletes) (.*) to \"(.+)\" with (.*)$")
    fun post(verb: String, body: String, path: String, ifMatch: String) {
        val code = when (verb) {
            "puts" -> org.eclipse.californium.core.coap.CoAP.Code.PUT
            "patches" -> org.eclipse.californium.core.coap.CoAP.Code.PATCH
            "deletes" -> org.eclipse.californium.core.coap.CoAP.Code.DELETE
            else -> org.eclipse.californium.core.coap.CoAP.Code.POST
        }
        val request = Request(code).apply {
            setURI(uri(path))
            if (verb != "deletes") setPayload(body)
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
        // "{digest}" is the digest of the representation this response carries, as HTTP's ETag has it.
        val digest = ContentAddress.address(ContentAddress.canonical(JsonTrees.plain(String(last.payload))))
        val value = httpETag.replace("{digest}", digest)
        val expected = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).copyOf(ETAG_BYTES)
        assertArrayEquals(expected, last.options.eTags.single())
    }

    @Then("the CoAP body matches the integrity of the link that led to it")
    fun matchesIntegrity() {
        val digest = MessageDigest.getInstance("SHA-256").digest(last.payload)
        assertEquals(linkIntegrity, "sha256-" + Base64.getEncoder().encodeToString(digest))
    }

    @Then("the CoAP problem type is {string}")
    fun problemType(type: String) = assertEquals(PROBLEMS + type, body().path("type").asText())

    @Then("the CoAP body links {word} to {string}")
    fun links(rel: String, href: String) = assertEquals(href, body().path("_links").path(rel).path("href").asText())

    @Then("the CoAP body offers the form {string}")
    fun offersForm(key: String) = assertTrue(body().path("_forms").has(key), body().toString())

    @Then("the CoAP body lists the item {string}")
    fun listsItem(href: String) =
        assertTrue(href in body().path("_links").path("item").map { it.path("href").asText() }, body().toString())

    @Then("the CoAP Location-Path is {string}")
    fun locationPath(path: String) = assertEquals(path, last.options.locationPathString)

    @Then("lamp {long} is at version {int}")
    fun version(id: Long, version: Int) = assertEquals(version, store.lamps.getValue(id).version)

    /** A Layer 1 read: it says No-Cache, and asks for the rung in Prefer. */
    private fun read(path: String, whole: Boolean = false) = Request.newGet().apply {
        setURI(if (whole) path else uri(path))
        options.addOption(Option(CoapOptions.NO_CACHE))
        if (prefer.isNotEmpty()) options.addOption(Option(CoapOptions.PREFER, prefer))
    }

    private fun send(request: Request) {
        last = checkNotNull(client.advanced(request)) { "no CoAP response to ${request.uri}" }.advanced()
    }

    private fun body(): JsonNode = ObjectMapper().readTree(last.payload)

    private fun uri(path: String) = "coap://localhost:${door.port}$path"

    private companion object {
        const val HAL_FORMS_PROFILE = "https://w3id.org/heart-rise/hal-forms"
        const val TIMEOUT_MS = 5_000L
        const val COAPS_TIMEOUT_MS = 3_000L

        /** The lamp server's key for "lamp-client" (LampCoapServer's test settings): "lamp-client-key!". */
        val LAMP_KEY = "lamp-client-key!".toByteArray()
        const val ETAG_BYTES = 8
        const val PROBLEMS = "https://w3id.org/heart-rise/problems/"
    }
}
