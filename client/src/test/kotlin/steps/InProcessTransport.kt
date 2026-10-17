package steps

import heart.HeartRequest
import heart.HeartResponse
import heart.Transport
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request

/**
 * ARTIE's requests, handed to an http4k server in this process instead of the
 * network: the same method, URL, headers and body, and the same answer back.
 * Nothing in between interprets them.
 */
class InProcessTransport(private val server: HttpHandler) : Transport {
    override fun send(request: HeartRequest): HeartResponse {
        val sent = request.headers.entries
            .fold(Request(Method.valueOf(request.method), request.url)) { r, (name, value) -> r.header(name, value) }
        val answer = server(request.body?.let { sent.body(it) } ?: sent)
        val headers = answer.headers.groupBy({ it.first }, { it.second.orEmpty() })
        return HeartResponse(answer.status.code, headers, answer.body.stream.readBytes())
    }
}
