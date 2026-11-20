package com.artie.heart.http4k.browser

import com.artie.heart.HtmlAssets
import com.artie.heart.HtmlView
import com.artie.heart.JsonTrees
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * A resource rendered from its representation, served with the page's own assets on a port the system picks: one act,
 * [relation], whose form has [schema]. It answers anything sent with 500, and counts what was sent in [sent].
 */
class RenderedPage(relation: String, schema: String) : AutoCloseable {
    val sent = AtomicInteger()

    private val page: String = HtmlView.render(
        JsonTrees.plain(
            """
            {"name":"sample","_links":{"self":{"href":"/sample"}},
             "_forms":{"$relation":{"title":"Measure","method":"POST",
               "_links":{"target":{"href":"/sample/act"}},"schema":$schema}}}
            """.trimIndent(),
        ) as Map<*, *>,
        "\"e\"",
        emptyList(),
        emptyList(),
    )

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val asset = HtmlAssets.byName[path.removePrefix(HtmlAssets.PATH)]
            val (status, type, body) = when {
                exchange.requestMethod != "GET" -> {
                    sent.incrementAndGet()
                    Triple(SERVER_ERROR, "text/plain", "")
                }
                path.startsWith(HtmlAssets.PATH) && asset != null -> Triple(OK, asset.mediaType, asset.text)
                else -> Triple(OK, HtmlView.MEDIA_TYPE, page)
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", type)
            // -1: no body at all.
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val address: String get() = "http://127.0.0.1:${server.address.port}/sample"

    override fun close() = server.stop(0)

    private companion object {
        const val OK = 200
        const val SERVER_ERROR = 500
    }
}
