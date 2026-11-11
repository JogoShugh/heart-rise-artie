package com.artie.heart.sims.library

import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.artie.heart.PROBLEM_TYPES
import com.fasterxml.jackson.databind.ObjectMapper
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
 * sends. The reply resource itself is heart-core's (Replies), served here over HTTP. Ana publishes the key she signs
 * her own requests with, at `/keys`.
 */
class CallerService(
    /** The keys a watch links as `hr:keys`, by key id: how Ana reads the keys her request's answers are signed with. */
    private val watchKeys: (String) -> Map<String, PublicKey>,
) {
    private val store = ReplyStore()
    private val mapper = ObjectMapper()

    /** The watch each reply answers for: the Location of the 201 that brought it into being, absolute. */
    private val watches = mutableMapOf<String, String>()

    /** Opens the reply named [reference], awaiting. */
    fun open(reference: String) = store.open(reference)

    /** The library brought the watch at [location] into being for Ana's request with reply [reference]. */
    fun asked(reference: String, location: String) {
        watches[reference] = location
    }

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

    /**
     * Checks every act on a reply against the watch Ana's request created, never against a link the act carries:
     * 409 where no request of Ana's awaits there, or the act names another watch; 401 where its signature does
     * not verify by the keys that watch links.
     */
    private fun signedActs(): Filter = Filter { next ->
        { request ->
            val watch = watches[request.uri.path.removePrefix("/replies/").substringBefore('/')]
            val keys = watch?.let(watchKeys).orEmpty()
            val key = SignedRequests.keyIdOf(request)?.let { keys[it] }
            when {
                request.method == Method.GET -> next(request)
                watch == null -> conflict("No request of Ana's awaits an answer here")
                key == null || !SignedRequests.signedBy(request, ORIGIN, key) ->
                    SignedRequests.unauthorized(request, REALM, keys.keys.firstOrNull() ?: LibraryKey.KEY_ID)
                namedWatch(request).let { it != null && it != watch } ->
                    conflict("The answer names another watch than the one Ana's request created, $watch")
                else -> next(request)
            }
        }
    }

    /** The watch an act says it answers for, if it says. */
    private fun namedWatch(request: Request): String? = runCatching {
        mapper.readTree(request.bodyString()).path("_links").path(Replies.IN_REPLY_TO).path("href").asText(null)
    }.getOrNull()

    private fun conflict(detail: String): Response = Response(Status.CONFLICT)
        .header("Content-Type", "application/problem+json")
        .body(
            mapper.writeValueAsString(
                mapOf(
                    "type" to PROBLEM_TYPES + "link-conflict",
                    "title" to "The act names another resource than the state keeps",
                    "status" to Status.CONFLICT.code,
                    "detail" to detail,
                    "rel" to Replies.IN_REPLY_TO,
                ),
            ),
        )

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
