package com.artie.heart.sims.library

import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.then
import java.security.PublicKey

/**
 * Ana's side of the exchange: a HEART service of replies, each awaiting until the library answers it. Only the library,
 * with its Bearer token, may act on a reply, and every act must carry the library's signature (RFC 9421) over what it
 * sends. The reply resource itself is heart-core's (Replies), served here over HTTP. Ana publishes the key she signs
 * her own requests with, at `/keys`.
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
            HeartSettings(title = REALM, realm = Replies.PROFILE.name, keys = mapOf(AnaKey.KEY_ID to AnaKey.pair.public)),
        ),
    )

    /** Refuses, with 401, every request that is not a GET and whose signature the library's key does not verify. */
    private fun signedActs(): Filter = Filter { next ->
        { request ->
            if (request.method == Method.GET || SignedRequests.signedBy(request, ORIGIN, libraryKey)) {
                next(request)
            } else {
                SignedRequests.unauthorized(request, REALM, LibraryKey.KEY_ID)
            }
        }
    }

    companion object {
        private const val REALM = "Ana's replies"

        /** Where Ana's service is: the origin her replies' signed target URIs name. */
        const val ORIGIN = "https://caller.example"

        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"

        /** The result Ana's replies want: which loan, and from when it can be renewed. The library fills it in. */
        const val RENEWABLE = """{"type": "object", "required": ["loan", "renewableFrom"], "properties": {""" +
            """"loan": {"type": "integer"}, "renewableFrom": {"type": "string", "format": "date"}}, """ +
            """"additionalProperties": true}"""
    }
}
