package com.artie.heart.tck

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.ScenarioScope
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/** One scenario's exchanges with the server under test: what was asked, and what came back. */
@Component
@ScenarioScope
class TckWorld(private val client: WebTestClient, private val fixture: TckFixture) {
    var id: Long = 0
    var heldETag: String? = null
    var heldHeaders: Map<String, String> = emptyMap()
    var linkIntegrity: String? = null
    private val results = mutableListOf<EntityExchangeResult<ByteArray>>()

    val last: EntityExchangeResult<ByteArray> get() = results.last()

    val json: JsonNode get() = ObjectMapper().readTree(last.responseBody ?: ByteArray(0))

    /** The fixture's resource. */
    val resource: String get() = fixture.address(id)

    /** When false, requests carry no credential: the scenarios about callers with none. */
    var credentialed = true

    fun get(path: String, headers: Map<String, String> = emptyMap()): EntityExchangeResult<ByteArray> {
        val spec = sender().get().uri(path)
        (credential() + headers).forEach { (name, value) -> spec.header(name, value) }
        return spec.exchange().expectBody().returnResult().also { results += it }
    }

    /** An act sent by [method], as its form says: a PATCH's body is a JSON Merge Patch, a DELETE has none. */
    fun post(
        path: String,
        body: String,
        headers: Map<String, String>,
        method: String = "POST",
    ): EntityExchangeResult<ByteArray> {
        val spec = sender().method(org.springframework.http.HttpMethod.valueOf(method)).uri(path)
        (credential() + headers).forEach { (name, value) -> spec.header(name, value) }
        val type = if (method == "PATCH") "application/merge-patch+json" else "application/json"
        val sent = if (method == "DELETE") spec else spec.header(HttpHeaders.CONTENT_TYPE, type).bodyValue(body)
        return sent.exchange().expectBody().returnResult().also { results += it }
    }

    private fun credential(): Map<String, String> =
        if (credentialed) mapOf(HttpHeaders.AUTHORIZATION to fixture.credential) else emptyMap()

    /** A client with no credential of its own drops any the server's test context sets on every request. */
    private val bare: WebTestClient by lazy {
        client.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
    }

    private fun sender(): WebTestClient = if (credentialed) client else bare

    /** Reads the resource and holds its ETag and the negotiation headers it was read with. */
    fun hold(headers: Map<String, String>) {
        heldHeaders = headers
        heldETag = get(resource, headers).responseHeaders.eTag
    }

    /** The forms of [body], by key. */
    fun forms(body: JsonNode = json): Map<String, JsonNode> =
        body.path("_forms").fields().asSequence().associate { it.key to it.value }

    /** Where each form of [body] points. */
    fun targets(body: JsonNode = json): List<String> =
        forms(body).values.map { it.path("_links").path("target").path("href").asText() }
}
