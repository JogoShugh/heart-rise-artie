package com.artie.heart.binding

import com.artie.heart.ContentAddress
import com.artie.heart.HeartResource
import com.artie.heart.JsonTrees
import com.artie.heart.Rung
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.ScenarioScope
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The exchanges of one scenario, shared by every step class: what was sent
 * and what came back, and how a lamp's validators are spelled.
 */
@Component
@ScenarioScope
class HttpWorld(
    private val client: WebTestClient,
    private val store: LampStore,
    @Qualifier(LampServer.BEAN) resource: HeartResource<*, *>,
) {
    var results: List<EntityExchangeResult<ByteArray>> = emptyList()
    val last: EntityExchangeResult<ByteArray> get() = results.last()

    /** The last response's body, as JSON. */
    val json: JsonNode get() = json(last)

    @Suppress("UNCHECKED_CAST")
    private val layers = (resource as HeartResource<Lamp, Any?>).layers

    fun get(path: String, headers: Map<String, String> = emptyMap()): EntityExchangeResult<ByteArray> =
        exchange(client.get().uri(path), headers)

    fun post(path: String, body: String, headers: Map<String, String>): EntityExchangeResult<ByteArray> =
        exchange(client.post().uri(path).header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue(body), headers)

    /** The latest read of a resource's Layer 1, a lamp's or a pull request's, whatever came after it. */
    fun layer1(): EntityExchangeResult<ByteArray> =
        results.last { it.method.name() == "GET" && it.url.path.matches(LAYER_1) }

    /** The negotiation headers [read] was sent with, for an act that repeats them. */
    fun negotiation(read: EntityExchangeResult<ByteArray>): Map<String, String> =
        listOf(HttpHeaders.ACCEPT, "Accept-Profile", "Prefer")
            .mapNotNull { name -> read.requestHeaders.getFirst(name)?.let { name to it } }.toMap()

    /**
     * Fills `{digest}` in a validator such as "seq-3.hsf.L0.{digest}": the digest of a
     * response's own body when it has one, or else of lamp 7's current representation in
     * that dialect and rung, as the server renders it.
     */
    fun expand(text: String, body: ByteArray? = null): String =
        DIGEST.replace(text) { match ->
            val (version, tag, rung) = match.destructured
            val digest = body?.takeIf { it.isNotEmpty() }
                ?.let { ContentAddress.address(ContentAddress.canonical(JsonTrees.plain(String(it)))) }
                ?: current(tag, Rung.valueOf(rung))
            "seq-$version.$tag.$rung.$digest"
        }

    fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private fun current(tag: String, rung: Rung): String {
        val lamp = store.lamps.getValue(LAMP)
        val representation = if (tag == "hf") layers.halForms(lamp) else layers.layer1(lamp, rung)
        return ContentAddress.address(ContentAddress.canonical(representation))
    }

    private fun exchange(
        spec: WebTestClient.RequestHeadersSpec<*>,
        headers: Map<String, String>,
    ): EntityExchangeResult<ByteArray> {
        headers.forEach { (name, value) -> spec.header(name, value) }
        return spec.exchange().expectBody().returnResult().also { results = results + it }
    }

    companion object {
        private val LAYER_1 = Regex("/lamps/\\d+|/repos/[^/]+/[^/]+/pulls/\\d+")

        /** What Accept asks for each dialect by. */
        fun accept(dialect: String) = when (dialect) {
            "hal-schema-forms" -> "application/hal+json"
            "hal-forms" -> "application/prs.hal-forms+json"
            "plain-json" -> "application/json"
            else -> error("unknown dialect $dialect")
        }

        fun names(list: String): Set<String> = list.split(",").map { it.trim() }.toSet()

        /** A validator whose digest the scenario leaves to be filled: "seq-3.hsf.L0.{digest}". */
        private val DIGEST = Regex("""seq-(\d+)\.(hsf|hf)\.(L\d)\.\{digest}""")

        /** Every lamp scenario works on lamp 7. */
        private const val LAMP = 7L
    }
}
