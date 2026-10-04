package heart

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A private HTTP cache: the subset of RFC 9111 that H.E.A.R.T. relies on
 * (SPEC §6.6), with no H.E.A.R.T.-specific logic.
 *
 * - A GET response with `max-age` (Layers 2 and 3: a year, `immutable`)
 *   is served from the cache, with no request, until it expires.
 * - A response with `no-cache` and an `ETag` (Layer 1) is stored but
 *   always revalidated: the next GET carries `If-None-Match`, and a `304`
 *   returns the stored response.
 * - `Vary` is honoured: an entry is used only when the request headers it
 *   names match. `no-store` and `Vary: *` are never stored.
 */
class CachingTransport(
    private val inner: Transport,
    private val now: () -> Instant = Instant::now,
) : Transport {

    private class Entry(
        val response: HeartResponse,
        val varied: Map<String, String?>,
        val freshUntil: Instant?,
        val etag: String?,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun holds(url: String): Boolean = entries.containsKey(url)

    override fun send(request: HeartRequest): HeartResponse {
        val entry = if (request.method == "GET") lookup(request) else null
        return when {
            request.method != "GET" -> inner.send(request)
            entry?.freshUntil?.isAfter(now()) == true -> entry.response
            else -> fetch(request, entry)
        }
    }

    private fun lookup(request: HeartRequest): Entry? =
        entries[request.url]?.takeIf { e -> e.varied.all { (name, value) -> request.header(name) == value } }

    /** A miss, or a stored entry that must be revalidated with its ETag. */
    private fun fetch(request: HeartRequest, entry: Entry?): HeartResponse {
        val etag = entry?.etag
        val sent = if (etag != null) request.copy(headers = request.headers + ("If-None-Match" to etag)) else request
        val response = inner.send(sent)
        if (response.status == NOT_MODIFIED && entry != null) return entry.response
        store(request, response)
        return response
    }

    private fun store(request: HeartRequest, response: HeartResponse) {
        val directives = response.header("Cache-Control").orEmpty().split(',').map { it.trim().lowercase() }
        val vary = response.header("Vary").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val maxAge = directives.firstOrNull { it.startsWith("max-age=") }?.removePrefix("max-age=")?.toLongOrNull()
        val etag = response.header("ETag")
        val revalidate = "no-cache" in directives
        val storable = response.status == OK && "no-store" !in directives && "*" !in vary &&
            ((maxAge != null && !revalidate) || (revalidate && etag != null))
        if (!storable) return
        entries[request.url] = Entry(
            response = response,
            varied = vary.associateWith { request.header(it) },
            freshUntil = if (revalidate) null else now().plusSeconds(maxAge ?: 0),
            etag = etag,
        )
    }

    private companion object {
        const val OK = 200
        const val NOT_MODIFIED = 304
    }
}
