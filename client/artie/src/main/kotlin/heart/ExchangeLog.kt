package heart

import heart.script.Exchange
import heart.script.Received
import heart.script.Sent
import java.time.Instant

/**
 * Keeps each exchange that passes it, for a run's capture (spec/PLAN-LINE-BY-LINE-REPORT.md): the request as
 * sent and the answer as received, each credential written `<token>`. It sits next to the wire, so what it
 * keeps is what went over it: a revalidation is kept, an answer from the cache is not.
 */
class ExchangeLog(private val next: Transport) : Transport {
    private val kept = mutableListOf<Exchange>()

    /** The exchanges kept since the last were taken. */
    fun taken(): List<Exchange> = synchronized(kept) { kept.toList().also { kept.clear() } }

    override fun send(request: HeartRequest): HeartResponse {
        val at = Instant.now()
        val started = System.nanoTime()
        val response = next.send(request)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        val sent = Sent(request.method, request.url, redacted(request.headers), request.body)
        val headers = redacted(response.headers.mapValues { (_, values) -> values.joinToString(", ") })
        val received = Received(response.status, headers, String(response.bytes, Charsets.UTF_8))
        synchronized(kept) { kept += Exchange(at, ms, sent, received) }
        return response
    }

    private fun redacted(headers: Map<String, String>): Map<String, String> =
        headers.mapValues { (name, value) -> if (name.lowercase() in CREDENTIALS) TOKEN else value }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val TOKEN = "<token>"
        val CREDENTIALS = setOf("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key")
    }
}
