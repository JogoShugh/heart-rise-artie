package heart

import java.security.MessageDigest
import java.util.Base64

/** Bytes that do not match the digest the server or the linking representation gave for them. */
class IntegrityException(message: String) : RuntimeException(message)

/**
 * Digest checks over a body's bytes exactly as received, before any parsing
 * (HAL-SCHEMA-FORMS-HR Rules 2 and 4). Digests are compared as bytes, so two
 * spellings of one Base64 value cannot disagree.
 */
object Integrity {

    /** SRI algorithm names (W3C Subresource Integrity) to JCA names. */
    private val sriAlgorithms = mapOf("sha256" to "SHA-256", "sha384" to "SHA-384", "sha512" to "SHA-512")

    /** RFC 9530 algorithm keys this client verifies. Others are ignored, as RFC 9530 allows. */
    private val reprAlgorithms = mapOf("sha-256" to "SHA-256", "sha-512" to "SHA-512")

    /**
     * A Subresource Integrity value such as "sha256-<Base64>". Several values may be
     * listed, separated by spaces; the bytes must match one of them. A value with
     * no supported algorithm is refused rather than skipped.
     */
    fun checkLink(url: String, bytes: ByteArray, integrity: String) {
        val expected = integrity.trim().split(Regex("\\s+")).mapNotNull { token ->
            val algorithm = sriAlgorithms[token.substringBefore('-')] ?: return@mapNotNull null
            algorithm to decode(token.substringAfter('-').substringBefore('?'))
        }
        if (expected.isEmpty()) throw IntegrityException("$url: no supported algorithm in integrity \"$integrity\"")
        if (expected.none { (algorithm, digest) -> digest(algorithm, bytes).contentEquals(digest) }) {
            throw IntegrityException("$url does not match its integrity \"$integrity\"")
        }
    }

    /**
     * An RFC 9530 Repr-Digest, an RFC 8941 dictionary such as
     * `sha-256=:<Base64>:, sha-512=:<Base64>:`. Every supported entry must match.
     */
    fun checkReprDigest(url: String, bytes: ByteArray, header: String) {
        header.split(',').map { it.trim() }.forEach { entry ->
            val algorithm = reprAlgorithms[entry.substringBefore('=').trim()] ?: return@forEach
            val value = entry.substringAfter('=').substringBefore(';').trim()
            val digest = if (value.length >= 2 && value.startsWith(':') && value.endsWith(':')) {
                decode(value.substring(1, value.length - 1))
            } else {
                null
            }
            if (digest == null || !digest(algorithm, bytes).contentEquals(digest)) {
                throw IntegrityException("$url does not match its Repr-Digest \"$header\"")
            }
        }
    }

    private fun digest(algorithm: String, bytes: ByteArray): ByteArray =
        MessageDigest.getInstance(algorithm).digest(bytes)

    private fun decode(base64: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(base64) }.getOrNull()
}

/**
 * Checks immutable layers before anything can store them (SPEC §6.5). It sits
 * below the cache, so refused bytes are never cached. It reads nothing from the
 * URL: the expected digest comes from the link that was followed
 * ([HeartRequest.integrity]) and, when the server sends one, from Repr-Digest.
 */
class IntegrityCheck(private val inner: Transport) : Transport {

    override fun send(request: HeartRequest): HeartResponse {
        val response = inner.send(request)
        if (request.method == "GET" && response.status in SUCCESS) {
            request.integrity?.let { Integrity.checkLink(request.url, response.bytes, it) }
            response.header("Repr-Digest")?.let { Integrity.checkReprDigest(request.url, response.bytes, it) }
        }
        return response
    }

    private companion object {
        val SUCCESS = 200..299
    }
}
