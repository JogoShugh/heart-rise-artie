package heart

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * One client's W3C Trace Context (https://www.w3.org/TR/trace-context/):
 * one trace id for everything the client does, a new span id per request.
 * The last span sent for each URL is kept, so an event about a request can
 * name that request's span.
 */
class Trace(val traceId: String = hex(TRACE_ID_BYTES)) {
    private val lastSpan = ConcurrentHashMap<String, String>()

    /** A new span for a request to [url], remembered as that URL's latest. */
    fun newSpan(url: String): String = hex(SPAN_ID_BYTES).also { lastSpan[url] = it }

    /** The span of the latest request to [url], or null if none was sent. */
    fun spanFor(url: String): String? = lastSpan[url]

    private companion object {
        const val TRACE_ID_BYTES = 16
        const val SPAN_ID_BYTES = 8
        val random = SecureRandom()

        fun hex(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Adds `traceparent` (W3C Trace Context §3.2: version 00, trace id, span id,
 * flags 01 = sampled) to every request, unless the request already has one.
 * Sits outermost with UserAgentTransport, above the client's cache.
 */
class TraceContextTransport(private val trace: Trace, private val next: Transport) : Transport {

    override fun send(request: HeartRequest): HeartResponse {
        if (request.header(TRACEPARENT) != null) return next.send(request)
        val span = trace.newSpan(request.url)
        return next.send(request.copy(headers = request.headers + (TRACEPARENT to "00-${trace.traceId}-$span-01")))
    }

    private companion object {
        const val TRACEPARENT = "traceparent"
    }
}
