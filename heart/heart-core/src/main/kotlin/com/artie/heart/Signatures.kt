package com.artie.heart

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/**
 * One signed request: the components it covers, the parameters they are signed with, and the signature over them
 * (RFC 9421). Its body is covered by its Content-Digest (RFC 9530).
 */
data class Signed(
    val method: String,
    val target: String,
    val contentDigest: String,
    val ifMatch: String,
    /** The parameters as a Signature-Input member (RFC 9421 §4.1): the covered components, then created and keyid. */
    val params: String,
    /** The signature, base64 (RFC 9421 §3.1 byte sequence). */
    val signature: String,
)

/** The key a sender signs with: its keyid (RFC 9421 §3.1) and its Ed25519 private key (RFC 8037). */
class Signer(private val key: PrivateKey, private val keyId: String) {
    /** The request [method] to [target] with [body] and [ifMatch], signed at [created] (seconds since the epoch). */
    fun sign(method: String, target: String, body: String, ifMatch: String, created: Long): Signed {
        val digest = Signatures.contentDigest(body)
        val members = "(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\")"
        val params = "$members;created=$created;keyid=\"$keyId\";alg=\"ed25519\""
        val signer = Signature.getInstance(ALGORITHM).apply {
            initSign(key)
            update(signatureBase(method, target, digest, ifMatch, params).toByteArray())
        }
        return Signed(method, target, digest, ifMatch, params, Base64.getEncoder().encodeToString(signer.sign()))
    }
}

/** Content-Digest (RFC 9530) and the check of a signed request (RFC 9421). */
object Signatures {
    /** The Content-Digest of [body], as SHA-256 (RFC 9530 §2). */
    fun contentDigest(body: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
        return "sha-256=:" + Base64.getEncoder().encodeToString(sha256) + ":"
    }

    /** Whether [signed] covers [body] and was made with [key]: the digest of [body] first, then the signature. */
    fun verify(signed: Signed, body: String, key: PublicKey): Boolean {
        if (contentDigest(body) != signed.contentDigest) return false
        val base = signatureBase(signed.method, signed.target, signed.contentDigest, signed.ifMatch, signed.params)
        return Signature.getInstance(ALGORITHM).run {
            initVerify(key)
            update(base.toByteArray())
            verify(Base64.getDecoder().decode(signed.signature))
        }
    }
}

private const val ALGORITHM = "Ed25519"

/** The signature base of RFC 9421 §2.5: the covered components, then the parameters signed with. */
private fun signatureBase(
    method: String,
    target: String,
    contentDigest: String,
    ifMatch: String,
    params: String,
): String =
    listOf(
        "\"@method\": $method",
        "\"@target-uri\": $target",
        "\"content-digest\": $contentDigest",
        "\"if-match\": $ifMatch",
        "\"@signature-params\": $params",
    ).joinToString("\n")
