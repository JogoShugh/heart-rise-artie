package com.artie.heart.gateway

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * The gateway's record of its upstream calls (spec/PLAN-LINE-BY-LINE-REPORT.md, step 4), kept at [file] when
 * GATEWAY_RECORD names one: each call one YAML document, with the W3C traceparent of the HEART request it was
 * serving, so a client's capture can put each call under the request that caused it. The request and answer
 * are written as they went, a JSON body as data, each credential as `<token>`.
 */
class UpstreamRecord(private val file: Path, private val mapper: ObjectMapper, private val upstreamUrl: String?) {
    private val serving = ThreadLocal<String?>()
    private val yaml = ObjectMapper(
        // Never a long value broken across lines with backslashes: a person reads this.
        YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE),
    )

    /** Around the gateway's handler: notes the traceparent of the request being served, while it is. */
    val servingFilter = Filter { next ->
        { request: Request ->
            serving.set(request.header(TRACEPARENT))
            try {
                next(request)
            } finally {
                serving.remove()
            }
        }
    }

    /** [upstream], each call it makes written to the record. */
    fun around(upstream: HttpHandler): HttpHandler = { request ->
        val at = Instant.now()
        val started = System.nanoTime()
        val response = upstream(request)
        write(at, (System.nanoTime() - started) / NANOS_PER_MILLI, request, response)
        response
    }

    private fun write(at: Instant, ms: Long, request: Request, response: Response) {
        val url = request.uri.toString().let { if (request.uri.host.isEmpty()) upstreamUrl.orEmpty() + it else it }
        val call = linkedMapOf(
            "at" to at.toString(),
            "ms" to ms,
            "traceparent" to serving.get(),
            "request" to linkedMapOf(
                "method" to request.method.name,
                "url" to url,
                "headers" to headers(request.headers),
                "body" to body(request.bodyString()),
            ).filterValues { it != null },
            "response" to linkedMapOf(
                "status" to response.status.code,
                "headers" to headers(response.headers),
                "body" to body(response.bodyString()),
            ).filterValues { it != null },
        )
        val document = "---\n" + yaml.writeValueAsString(call)
        synchronized(this) {
            Files.writeString(file, document, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    private fun headers(headers: List<Pair<String, String?>>): Map<String, String?> =
        headers.associate { (name, value) -> name to if (name.lowercase() in CREDENTIALS) TOKEN else value }

    /** A body as data when it is JSON, as text otherwise; none when empty. */
    private fun body(text: String): Any? = when {
        text.isEmpty() -> null
        else -> runCatching { mapper.readTree(text) as JsonNode }.getOrElse { text }
    }

    private companion object {
        const val TRACEPARENT = "traceparent"
        const val TOKEN = "<token>"
        const val NANOS_PER_MILLI = 1_000_000L
        val CREDENTIALS = setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key")
    }
}
