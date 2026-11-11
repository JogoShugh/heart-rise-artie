package com.artie.heart.sims.library

import com.artie.heart.Replies
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.ReplyStore
import com.artie.heart.Signatures
import com.artie.heart.Signed
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import java.security.PublicKey

/**
 * Ana's side of the exchange: a HEART service of replies, each awaiting until the library answers it. Only the library,
 * with its Bearer token, may act on a reply, and every act must carry the library's signature (RFC 9421) over what it
 * sends. The reply resource itself is heart-core's (Replies), served here over HTTP.
 */
class CallerService(private val libraryKey: PublicKey = LibraryKey.pair.public) {
    private val store = ReplyStore()

    /** Opens the reply named [reference], awaiting. */
    fun open(reference: String) = store.open(reference)

    /** Whether a reply named [reference] is open. */
    fun has(reference: String): Boolean = store.get(reference) != null

    /** The caller's HEART service: the replies, served to the library's Bearer token, each act signed by the library. */
    val handler: HttpHandler = signedActs().then(
        HeartHttp(
            listOf(Replies.resource(store, RENEWABLE)),
            Credentials.parse(users = "", tokens = Tokens.sha256(LIBRARY_TOKEN)),
            HeartSettings(title = REALM, realm = Replies.PROFILE.name),
        ),
    )

    /**
     * Refuses, with 401, every request that is not a GET and whose signature does not verify (RFC 9421, RFC 9530):
     * as `signature-required` when it carries none, `invalid-signature` when it carries one that does not verify.
     */
    private fun signedActs(): Filter = Filter { next ->
        { request ->
            when {
                request.method == Method.GET || signedBy(request) -> next(request)
                request.header("Signature") == null -> unauthorized("signature-required", "A signature is required")
                else -> unauthorized("invalid-signature", "The signature does not verify")
            }
        }
    }

    /** Whether [request] carries a signature that verifies, over its method, target, digest, If-Match and body. */
    private fun signedBy(request: Request): Boolean {
        val params = request.header("Signature-Input")?.removePrefix("sig1=") ?: return false
        val signature = request.header("Signature")?.removePrefix("sig1=:")?.removeSuffix(":") ?: return false
        val signed = Signed(
            method = request.method.name,
            target = "https://caller.example${request.uri.path}",
            contentDigest = request.header("Content-Digest") ?: return false,
            ifMatch = request.header("If-Match") ?: return false,
            params = params,
            signature = signature,
        )
        return Signatures.verify(signed, request.bodyString(), libraryKey)
    }

    /**
     * 401 as an RFC 9457 problem of [type], saying how to authenticate: by the Signature scheme the profile declares,
     * over the components and by the key RFC 9421's Accept-Signature names.
     */
    private fun unauthorized(type: String, title: String): Response = Response(Status.UNAUTHORIZED)
        .header("Content-Type", "application/problem+json")
        .header("WWW-Authenticate", "Signature realm=\"$REALM\"")
        .header("Accept-Signature", ACCEPT_SIGNATURE)
        .body(
            """{"type": "$PROBLEM_TYPES$type", "title": "$title", "status": 401, """ +
                """"detail": "Each act on a reply is signed by the library's key, ${LibraryKey.KEY_ID}."}""",
        )

    companion object {
        private const val REALM = "Ana's replies"

        /** RFC 9421 §5.1: what a signature on a reply's act covers, and the key it is made with. */
        val ACCEPT_SIGNATURE =
            "sig1=(\"@method\" \"@target-uri\" \"content-digest\" \"if-match\");" +
                "keyid=\"${LibraryKey.KEY_ID}\";alg=\"ed25519\""

        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"

        /** The result Ana's replies want: which loan, and from when it can be renewed. The library fills it in. */
        const val RENEWABLE = """{"type": "object", "required": ["loan", "renewableFrom"], "properties": {""" +
            """"loan": {"type": "integer"}, "renewableFrom": {"type": "string", "format": "date"}}, """ +
            """"additionalProperties": true}"""
    }
}
