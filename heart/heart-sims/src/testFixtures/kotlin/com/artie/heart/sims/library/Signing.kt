package com.artie.heart.sims.library

import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.Signatures
import com.artie.heart.Signed
import com.artie.heart.Signer
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey

/** A party's signing key (Ed25519, RFC 8037), named by its key id, and a signer that only claims it. */
open class SigningKey(val keyId: String) {
    val pair: KeyPair by lazy { KeyPairGenerator.getInstance("Ed25519").generateKeyPair() }

    /** The party's signer: its requests are signed with [pair], under [keyId]. */
    val signer: Signer by lazy { Signer(pair.private, keyId) }

    /** A signer that claims [keyId] with a key of its own: what a forgery is signed with. */
    val otherSigner: Signer by lazy { Signer(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().private, keyId) }
}

/** The library's signing key. Its public half is what Ana's reply checks a result against. */
object LibraryKey : SigningKey("library-2026-11") {
    const val KEY_ID = "library-2026-11"
}

/** Ana's signing key. Her request names the set it is in (`hr:keys`); the library checks her request by it. */
object AnaKey : SigningKey("ana-2026-11") {
    const val KEY_ID = "ana-2026-11"
}

/**
 * Signed requests (RFC 9421 over RFC 9530's Content-Digest), as both parties check them: whether a request is
 * signed by a key, and the 401 that says why not and how to sign.
 */
object SignedRequests {
    private val KEY_ID = Regex("keyid=\"([^\"]+)\"")

    /** The key id a request's signature names, if it carries one. */
    fun keyIdOf(request: Request): String? =
        request.header("Signature-Input")?.let { KEY_ID.find(it)?.groupValues?.get(1) }

    /** Whether [request], sent to [origin], carries a signature [key] verifies: over its method, target and body. */
    fun signedBy(request: Request, origin: String, key: PublicKey): Boolean {
        val params = request.header("Signature-Input")?.removePrefix("sig1=") ?: return false
        val signature = request.header("Signature")?.removePrefix("sig1=:")?.removeSuffix(":") ?: return false
        val signed = Signed(
            method = request.method.name,
            target = origin + request.uri.path,
            contentDigest = request.header("Content-Digest") ?: return false,
            ifMatch = request.header("If-Match") ?: return false,
            params = params,
            signature = signature,
        )
        return Signatures.verify(signed, request.bodyString(), key)
    }

    /** RFC 9421 §5.1: what a signature on an act covers, and the key it is to be made with. */
    fun acceptSignature(keyId: String): String =
        "sig1=(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\");keyid=\"$keyId\";alg=\"ed25519\""

    /**
     * 401 for [request]: `signature-required` when it carries no signature, `invalid-signature` when it carries one
     * that does not verify; an RFC 9457 problem that says how to authenticate, by the Signature scheme in [realm],
     * with the key [keyId].
     */
    fun unauthorized(request: Request, realm: String, keyId: String): Response {
        val (type, title) = if (request.header("Signature") == null) {
            "signature-required" to "A signature is required"
        } else {
            "invalid-signature" to "The signature does not verify"
        }
        return Response(Status.UNAUTHORIZED)
            .header("Content-Type", "application/problem+json")
            .header("WWW-Authenticate", "Signature realm=\"$realm\"")
            .header("Accept-Signature", acceptSignature(keyId))
            .body(
                """{"type": "$PROBLEM_TYPES$type", "title": "$title", "status": 401, """ +
                    """"detail": "Each act here is signed by the key $keyId."}""",
            )
    }
}
