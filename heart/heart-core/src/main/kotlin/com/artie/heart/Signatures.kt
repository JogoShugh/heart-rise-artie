package com.artie.heart

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.HexFormat

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

    /** Where a HEART service that signs publishes the keys it signs with, linked from its index as `hr:keys`. */
    const val KEYS = "/keys"

    /** The media type of a set of keys (RFC 7517 §8.5). */
    const val JWK_SET = "application/jwk-set+json"

    /** [key] as a JWK (RFC 7517, RFC 8037): an OKP key on Ed25519, named [keyId], for signatures. */
    fun jwk(key: PublicKey, keyId: String): Map<String, String> = linkedMapOf(
        "kty" to "OKP",
        "crv" to ALGORITHM,
        "kid" to keyId,
        "alg" to "EdDSA",
        "use" to "sig",
        "x" to Base64.getUrlEncoder().withoutPadding().encodeToString(raw(key)),
    )

    /** The keys, by key id, as a JWK Set (RFC 7517 §5). */
    fun keySet(keys: Map<String, PublicKey>): Map<String, Any> = mapOf("keys" to keys.map { (id, key) -> jwk(key, id) })

    /** The Ed25519 public key a JWK names (RFC 8037 §2); refused when it names another kind of key. */
    fun keyOf(jwk: Map<*, *>): PublicKey {
        require(jwk["kty"] == "OKP" && jwk["crv"] == ALGORITHM) { "not an Ed25519 key: $jwk" }
        val raw = Base64.getUrlDecoder().decode(jwk["x"].toString())
        return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(X509_PREFIX + raw))
    }
}

private const val ALGORITHM = "Ed25519"

/** An Ed25519 public key's X.509 encoding: this fixed prefix (RFC 8410), then the key's 32 bytes. */
private val X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100")

/** The key's bytes, as RFC 8037 §2 writes them in `x`: its X.509 encoding without the prefix. */
private fun raw(key: PublicKey): ByteArray = key.encoded.copyOfRange(X509_PREFIX.size, key.encoded.size)

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
