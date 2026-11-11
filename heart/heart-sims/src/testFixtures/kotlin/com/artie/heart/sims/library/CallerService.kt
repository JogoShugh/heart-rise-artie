package com.artie.heart.sims.library

import com.artie.heart.Replies
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
            HeartSettings(title = "Ana's replies", realm = Replies.PROFILE.name),
        ),
    )

    /** Refuses, with 401, every request that is not a GET and whose signature does not verify (RFC 9421, RFC 9530). */
    private fun signedActs(): Filter = Filter { next ->
        { request ->
            if (request.method == Method.GET || signedBy(request)) next(request) else unauthorized()
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

    private fun unauthorized(): Response = Response(Status.UNAUTHORIZED)
        .header("Content-Type", "application/problem+json")
        .body("""{"title": "The signature does not verify", "status": 401}""")

    companion object {
        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"

        /** The result Ana's replies want: which loan, and from when it can be renewed. The library fills it in. */
        const val RENEWABLE = """{"type": "object", "required": ["loan", "renewableFrom"], "properties": {""" +
            """"loan": {"type": "integer"}, "renewableFrom": {"type": "string", "format": "date"}}, """ +
            """"additionalProperties": true}"""
    }
}
