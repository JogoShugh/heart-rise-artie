package com.artie.gardenbed.bdd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.ScenarioScope
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.WebTestClient

/** One HTTP exchange as the scenario saw it: what was asked, and the raw bytes that came back. */
data class Exchange(val path: String, val status: Int, val headers: HttpHeaders, val bytes: ByteArray) {
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

    fun get(path: String, headers: Map<String, String> = emptyMap()): Exchange {
        val result = client.get().uri(path)
            .headers { h -> headers.forEach { (k, v) -> h.set(k, v) } }
            .exchange()
            .expectBody().returnResult()
        val body = result.responseBodyContent ?: ByteArray(0)
        val exchange = Exchange(path, result.status.value(), result.responseHeaders, body)
        exchanges += exchange
        return exchange
    }

    companion object {
        const val HAL_SCHEMA_FORMS_ACCEPT =
            "application/hal+json;profile=\"https://github.com/jbadeau/hal-schema-forms\""
        const val FORMS_REL = "http://api.example.org/rels/forms"
    }
}
