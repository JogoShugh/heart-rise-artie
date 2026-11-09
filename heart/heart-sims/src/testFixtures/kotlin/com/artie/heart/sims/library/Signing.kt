package com.artie.heart.sims.library

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/** The library's signing key (Ed25519, RFC 8037). Its public half is what Ana's reply checks a result against. */
object LibraryKey {
    const val KEY_ID = "library-2026-11"
    val pair: KeyPair by lazy { KeyPairGenerator.getInstance("Ed25519").generateKeyPair() }
}

/** One signed request: the components it covers, its parameters, and the signature over them (RFC 9421). */
data class Signed(
    val method: String,
    val target: String,
    val contentDigest: String,
    val ifMatch: String,
    val created: Long,
    val signature: String,
)

/** The `signed` module, as the sims need it: a Content-Digest (RFC 9530) and an Ed25519 signature over an act. */
object Signatures {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    /** The Content-Digest of [body], as SHA-256 (RFC 9530). */
    fun contentDigest(body: String): String =
        "sha-256=:" + encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(body.toByteArray())) + ":"

    /** The request [method] to [target], with its [contentDigest] and [ifMatch], signed with the library's key. */
    fun sign(method: String, target: String, contentDigest: String, ifMatch: String, created: Long): Signed {
        val base = base(method, target, contentDigest, ifMatch, created)
        val signer = Signature.getInstance("Ed25519").apply {
            initSign(LibraryKey.pair.private)
            update(base.toByteArray())
        }
        return Signed(method, target, contentDigest, ifMatch, created, encoder.encodeToString(signer.sign()))
    }

    /** Whether [signed] was made with [key]: the base rebuilt from what it covers, checked against its signature. */
    fun verify(signed: Signed, key: PublicKey): Boolean {
        val base = base(signed.method, signed.target, signed.contentDigest, signed.ifMatch, signed.created)
        return Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(base.toByteArray())
            verify(decoder.decode(signed.signature))
        }
    }

    /** The parameters the signature is made with, as a Signature-Input member (RFC 9421 §4.1). */
    fun params(created: Long): String =
        "(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\");created=$created;keyid=\"${LibraryKey.KEY_ID}\";alg=\"ed25519\""

    /** The signature base of RFC 9421 §2.5: the covered components, then the parameters they are signed with. */
    private fun base(method: String, target: String, contentDigest: String, ifMatch: String, created: Long): String =
        listOf(
            "\"@method\": $method",
            "\"@target-uri\": $target",
            "\"content-digest\": $contentDigest",
            "\"if-match\": $ifMatch",
            "\"@signature-params\": ${params(created)}",
        ).joinToString("\n")
}
