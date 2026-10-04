package heart

import java.security.MessageDigest

/** A content-addressed response whose bytes do not hash to its URL. */
class ContentAddressMismatch(url: String, actual: String) :
    RuntimeException("$url does not hash to its address (sha256 of the body is $actual)")

/**
 * SPEC §6.5: the address is the SHA-256 of the response body, byte for
 * byte. This sits below the cache, so bytes that fail the check are
 * refused before anything can store them under an immutable URL.
 * A truncated digest in the URL (e.g. 16 hex) is compared as a prefix.
 * JSON is UTF-8 (RFC 8259), so the body text re-encoded as UTF-8 is the
 * bytes that were sent.
 */
class ContentAddressCheck(private val inner: Transport) : Transport {
    private val address = Regex("""/sha256-([0-9a-f]+)$""")

    override fun send(request: HeartRequest): HeartResponse {
        val response = inner.send(request)
        val expected = address.find(request.url.substringBefore('?'))?.groupValues?.get(1)
        if (request.method == "GET" && response.status in SUCCESS && expected != null) {
            val actual = MessageDigest.getInstance("SHA-256").digest(response.body.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            if (!actual.startsWith(expected)) throw ContentAddressMismatch(request.url, actual)
        }
        return response
    }

    private companion object {
        val SUCCESS = 200..299
    }
}
