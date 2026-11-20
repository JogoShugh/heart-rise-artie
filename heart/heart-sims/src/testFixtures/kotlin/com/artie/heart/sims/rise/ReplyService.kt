package com.artie.heart.sims.rise

import com.artie.heart.Replies
import com.artie.heart.ReplyStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.artie.heart.sims.library.SignedRequests
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
 * A caller's replies, served at [origin]: heart-core's reply resource (Replies), each awaiting until the party it
 * asked answers it. Every act must carry a signature (RFC 9421) by a key of the set the request it answers links
 * (`hr:keys` on that request's resource, read by [requestKeys]), and may name only that request (`hr:in-reply-to`).
 * The answering party presents one of [tokens]. A result must be shaped as [result]. The caller's own keys, which
 * it signs its requests with, are published at `/keys`.
 */
class ReplyService(
    private val origin: String,
    private val realm: String,
    tokens: List<String>,
    result: String,
    ownKeys: Map<String, PublicKey>,
    /** The keys the request at an address links as `hr:keys`, by key id. */
    private val requestKeys: (String) -> Map<String, PublicKey>,
) {
    private val store = ReplyStore()
    private val mapper = ObjectMapper()

    /** The request each reply answers for: the Location of the 201 that brought it into being, absolute. */
    private val requests = mutableMapOf<String, String>()

    /** Opens the reply named [reference], awaiting, before the request that names it is sent. */
    fun open(reference: String) {
        store.open(reference)
        requests.remove(reference)
    }

    /** The request at [location] was brought into being for the caller's request with reply [reference]. */
    fun asked(reference: String, location: String) {
        requests[reference] = location
    }

    /** The reply [reference]'s state now: its state, and the result or problem it took, if any. */
    fun state(reference: String): Map<String, Any?>? = store.get(reference)?.let {
        val read = Request(Method.GET, "/replies/$reference").header("Accept", "application/hal+json")
            .header("Authorization", "Bearer $reader")
        mapper.readValue(handler(read).bodyString(), Map::class.java).mapKeys { it.key.toString() }
    }

    /** A token the service admits, for reading a reply as an answering party would. */
    private val reader = tokens.first()

    val handler: HttpHandler = signedActs().then(
        HeartHttp(
            listOf(Replies.resource(store, result)),
            Credentials.parse(users = "", tokens = tokens.joinToString(",") { Tokens.sha256(it) }),
            HeartSettings(title = realm, realm = Replies.PROFILE.name, keys = ownKeys),
        ),
    )

    /** Each act on a reply: signed by a key the request it answers links, and naming no other request. */
    private fun signedActs(): Filter = Filter { next ->
        { request ->
            val reference = request.uri.path.removePrefix("/replies/").substringBefore('/')
            val asked = requests[reference]
            val keys = asked?.let(requestKeys).orEmpty()
            val key = SignedRequests.keyIdOf(request)?.let { keys[it] }
            when {
                request.method == Method.GET -> next(request)
                asked == null -> Response(Status.CONFLICT)
                key == null || !SignedRequests.signedBy(request, origin, key) ->
                    SignedRequests.unauthorized(request, realm, keys.keys.firstOrNull() ?: "")
                else -> next(request)
            }
        }
    }
}
