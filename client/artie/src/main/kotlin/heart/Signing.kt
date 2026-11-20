package heart

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.time.Instant
import java.util.Base64

/**
 * The key a host signs ARTIE's acts with (hosted.feature): its key id, as RFC 9421 §3.1 names it in `keyid`, and its
 * Ed25519 private key (RFC 8037). The host publishes the public half; ARTIE never sees where.
 */
class SigningKey(val keyId: String, internal val key: PrivateKey) {
    override fun toString() = "SigningKey($keyId, ****)"
}

/**
 * Signs each act on its way to the wire (RFC 9421), over its method, target URI, Content-Digest (RFC 9530) and
 * If-Match, so the party it calls can tell who asked and that the body arrived as sent. Reads go unsigned: they
 * change nothing, and a cache answers many of them. An act with no If-Match goes unsigned too, since what is
 * signed names it; ARTIE sends one with every act it makes (SPEC §6.3).
 */
class SigningTransport(
    private val signing: SigningKey,
    private val next: Transport,
    private val clock: () -> Instant = Instant::now,
) : Transport {
    override fun send(request: HeartRequest): HeartResponse {
        val ifMatch = request.header(IF_MATCH)
        if (request.method in READS || ifMatch == null) return next.send(request)
        val digest = contentDigest(request.body.orEmpty())
        val params = "$COVERED;created=${clock().epochSecond};keyid=\"${signing.keyId}\";alg=\"ed25519\""
        val base = listOf(
            "\"@method\": ${request.method}",
            "\"@target-uri\": ${request.url}",
            "\"content-digest\": $digest",
            "\"if-match\": $ifMatch",
            "\"@signature-params\": $params",
        ).joinToString("\n")
        val signature = Signature.getInstance(ALGORITHM).run {
            initSign(signing.key)
            update(base.toByteArray())
            Base64.getEncoder().encodeToString(sign())
        }
        val headers = mapOf(
            "Content-Digest" to digest,
            "Signature-Input" to "sig1=$params",
            "Signature" to "sig1=:$signature:",
        )
        return next.send(request.copy(headers = request.headers + headers))
    }

    private companion object {
        const val ALGORITHM = "Ed25519"
        const val IF_MATCH = "If-Match"
        const val COVERED = "(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\")"
        val READS = setOf("GET", "HEAD", "OPTIONS")

        /** The Content-Digest of [body], as SHA-256 (RFC 9530 §2). */
        fun contentDigest(body: String): String =
            "sha-256=:" + Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(body.toByteArray())) + ":"
    }
}
