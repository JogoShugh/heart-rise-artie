package com.artie.gardenbed.bdd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.ScenarioScope
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient

/** One HTTP exchange as the scenario saw it: what was asked, and the raw bytes that came back. */
data class Exchange(
    val path: String,
    val requestHeaders: Map<String, String>,
    val status: Int,
    val headers: HttpHeaders,
    val bytes: ByteArray,
) {
    val json: JsonNode by lazy { ObjectMapper().readTree(bytes) }
}

/**
 * Per-scenario HTTP state shared by every step class. Requests always ask
 * for the hal-schema-forms dialect; the HAL-FORMS dialect gets its own
 * steps when L5 is covered.
 */
// @Component lets the kotlin-spring plugin open the class, so the scenario-scope proxy can subclass it.
@Component
@ScenarioScope
class HttpWorld(private val client: WebTestClient) {
    val exchanges = mutableListOf<Exchange>()

    val last: Exchange get() = exchanges.last()

    fun get(path: String, headers: Map<String, String> = emptyMap()): Exchange =
        record(path, headers, client.get().uri(path).headers { h -> headers.forEach { (k, v) -> h.set(k, v) } })

    fun post(path: String, json: String, headers: Map<String, String> = emptyMap()): Exchange =
        record(
            path,
            headers,
            client.post().uri(path)
                .headers { h -> headers.forEach { (k, v) -> h.set(k, v) } }
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(json),
        )

    /** The latest response from a bed resource (Layer 1), whatever came after it. */
    fun lastLayer1(): Exchange = exchanges.last { it.path.startsWith("/beds/") && !it.path.contains("/actions/") }

    private fun record(
        path: String,
        headers: Map<String, String>,
        spec: WebTestClient.RequestHeadersSpec<*>,
    ): Exchange {
        val result = spec.exchange().expectBody().returnResult()
        val body = result.responseBodyContent ?: ByteArray(0)
        val exchange = Exchange(path, headers, result.status.value(), result.responseHeaders, body)
        exchanges += exchange
        return exchange
    }

    companion object {
        const val HAL_SCHEMA_FORMS_ACCEPT =
            "application/hal+json;profile=\"https://github.com/jbadeau/hal-schema-forms\""
        const val HAL_FORMS_ACCEPT = "application/prs.hal-forms+json"
        const val FORMS_REL = "http://api.example.org/rels/forms"

        fun acceptFor(dialect: String): String = when (dialect) {
            "hal-schema-forms" -> HAL_SCHEMA_FORMS_ACCEPT
            "hal-forms" -> HAL_FORMS_ACCEPT
            else -> error("unknown dialect $dialect")
        }
    }
}
