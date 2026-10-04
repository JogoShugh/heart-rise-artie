package heart

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The real wire: java.net.http, no third-party HTTP stack. Sends exactly
 * the headers ARTIE built and returns status, every header, and the body
 * as text. It follows no redirects and keeps no cache of its own.
 */
class HttpTransport(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : Transport {

    override fun send(request: HeartRequest): HeartResponse {
        val body = request.body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(URI(request.url)).method(request.method, body)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return HeartResponse(response.statusCode(), response.headers().map(), response.body())
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 5L
    }
}
