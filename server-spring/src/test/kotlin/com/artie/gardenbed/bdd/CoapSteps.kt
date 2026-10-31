package com.artie.gardenbed.bdd

import com.artie.heart.coap.CoapFrontDoor
import com.artie.heart.coap.CoapOptions
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.After
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.coap.Option
import org.eclipse.californium.core.coap.Request
import org.eclipse.californium.core.coap.Response
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.core.network.CoapEndpoint
import org.eclipse.californium.elements.config.Configuration
import org.eclipse.californium.elements.config.UdpConfig
import org.junit.jupiter.api.Assertions.assertEquals

/** Steps for SPEC §6.0.1: the server over CoAP, asked by a real CoAP client on UDP, beside HTTP. */
class CoapSteps(private val door: CoapFrontDoor, private val http: HttpWorld) {
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

    @After
    fun shutdown() {
        if (this::last.isInitialized) client.shutdown()
    }

    /** A Layer 1 read: it says No-Cache, and asks for the rung in Prefer. */
    @When("a CoAP client gets {string} with Prefer {string}")
    fun get(path: String, prefer: String) {
        val request = Request.newGet().apply {
            setURI("coap://localhost:${door.port}$path")
            options.addOption(Option(CoapOptions.NO_CACHE))
            if (prefer.isNotEmpty()) options.addOption(Option(CoapOptions.PREFER, prefer))
        }
        last = checkNotNull(client.advanced(request)) { "no CoAP response to ${request.uri}" }.advanced()
    }

    @Then("the CoAP body is the same JSON as the HTTP body")
    fun sameAsHttp() = assertEquals(http.last.json, ObjectMapper().readTree(last.payload))

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
