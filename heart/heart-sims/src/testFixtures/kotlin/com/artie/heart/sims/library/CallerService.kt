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
import java.time.Instant

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

    /** Opens the reply named [reference], awaiting: a new reply, with no watch and no history yet. */
    fun open(reference: String) {
        store.open(reference)
        watches.remove(reference)
        histories.remove(reference)
    }

    /** The library brought the watch at [location] into being for Ana's request with reply [reference]. */
    fun asked(reference: String, location: String) {
        watches[reference] = location
    }

    /** Whether a reply named [reference] is open. */
    fun has(reference: String): Boolean = store.get(reference) != null

    /** Each reply's changes, oldest first: when, the ETag each replaced, and the signature each came with. */
    private val histories = mutableMapOf<String, MutableList<Map<String, Any?>>>()

    /** The caller's HEART service: the replies, served to the library's Bearer token, each act signed by the library. */
    val handler: HttpHandler = history().then(signedActs()).then(
        HeartHttp(
            listOf(Replies.resource(store, RENEWABLE) { reply -> mapOf(VERSION_HISTORY to historyLink(reply.reference)) }),
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

    /**
     * A reply's history (RFC 5829 `version-history`), served at its own address: and each act that changed a
     * reply, recorded with what proves it, the signature it came with. A repeat answered as the first changes
     * nothing, so it records nothing.
     */
    private fun history(): Filter = Filter { next ->
        { request ->
            val reference = request.uri.path.removePrefix("/replies/").substringBefore('/')
            if (request.method == Method.GET && request.uri.path.endsWith("/history")) {
                Response(Status.OK).header("Content-Type", "application/hal+json").body(
                    mapper.writeValueAsString(
                        mapOf(
                            "_links" to mapOf("self" to historyLink(reference), "up" to mapOf("href" to "/replies/$reference")),
                            "changes" to histories[reference].orEmpty(),
                        ),
                    ),
                )
            } else {
                next(request).also { answer ->
                    val changed = answer.status.successful && answer.header("ETag") != request.header("If-Match")
                    if (request.method != Method.GET && changed) record(reference, request)
                }
            }
        }
    }

    private fun record(reference: String, request: Request) {
        val changes = histories.getOrPut(reference) { mutableListOf() }
        val number = changes.size + 1
        val at = { n: Int -> mapOf("href" to "/replies/$reference/history#change-$n") }
        changes += linkedMapOf(
            "at" to Instant.now().toString(),
            "replaced" to request.header("If-Match"),
            "signatureInput" to request.header("Signature-Input"),
            "signature" to request.header("Signature"),
            "_links" to listOfNotNull("self" to at(number), if (number > 1) PREDECESSOR to at(number - 1) else null)
                .toMap(),
        )
    }

    private fun historyLink(reference: String) = mapOf("href" to "/replies/$reference/history", "type" to "application/hal+json")

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

        /** RFC 5829: a resource's history, and a version's predecessor. */
        const val VERSION_HISTORY = "version-history"
        const val PREDECESSOR = "predecessor-version"

        /** Where Ana's service is: the origin her replies' signed target URIs name. */
        const val ORIGIN = "http://localhost:8141"

        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"

        /** The result Ana's replies want: which loan, and from when it can be renewed. The library fills it in. */
        const val RENEWABLE = """{"type": "object", "required": ["loan", "renewableFrom"], "properties": {""" +
            """"loan": {"type": "integer"}, "renewableFrom": {"type": "string", "format": "date"}}, """ +
            """"additionalProperties": true}"""
    }
}
