package com.artie.heart

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Ana's reply: a caller's own resource, where the answers to its request land (RISE, spec/ideas/rise-deep-dive.md).
 * Its reference is the name the request gave it, such as `r-7f3`; its number is that name's hexadecimal part.
 * A failed reply keeps the failure's problem document, so its owner can see why.
 */
data class Reply(
    val reference: String,
    val standing: String = AWAITING,
    val percent: Int? = null,
    val note: String? = null,
    val result: Map<String, Any?>? = null,
    val problem: Map<String, Any?>? = null,
    /** The request the reply answers, as its first answer named it (`hr:in-reply-to`). */
    val inReplyTo: String? = null,
    val version: Int = 1,
) {
    val number: Long get() = reference.removePrefix("r-").toLong(HEX)

    companion object {
        private const val HEX = 16
        const val AWAITING = "awaiting"
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}

/** The replies a service holds. A reply is replaced only if nothing else changed it since it was read. */
class ReplyStore {
    private val replies = ConcurrentHashMap<String, Reply>()

    /** Opens the reply named [reference], awaiting. */
    fun open(reference: String) {
        replies[reference] = Reply(reference)
    }

    /** The reply named [reference], or null when there is none. */
    fun get(reference: String): Reply? = replies[reference]

    /** [next] in place of [current], only if nothing else changed the reply since; a stale write otherwise. */
    fun replace(current: Reply, next: Reply): Reply {
        if (!replies.replace(current.reference, current, next)) {
            throw ConcurrentWriteException("reply ${current.reference} changed since it was read")
        }
        return next
    }
}

/**
 * The reply resource. While it awaits, it takes progress, then one answer: its result, or a failure. Each is a
 * PATCH of the reply itself, whose `state` names the act: progress a JSON Merge Patch (RFC 7396) that keeps the
 * reply awaiting, the result one that completes it, the failure an RFC 9457 problem document that fails it. A
 * repeat of the result it has is replayed (SPEC §6.2, decision 8); any other act on an answered reply is not
 * offered.
 */
object Replies {
    private val mapper = ObjectMapper()

    /** A result or a problem as plain values, which heart-core canonicalises for the validator (JCS). */
    private val PLAIN = object : TypeReference<Map<String, Any?>>() {}

    /** Any result at all: the shape of a reply whose owner names none. */
    const val ANY_RESULT = """{"type": "object"}"""

    /** The problem document a failure is sent as (RFC 9457). */
    const val PROBLEM = "application/problem+json"

    private const val FULL = 100

    /** The link to the request an answer answers. */
    const val IN_REPLY_TO = "hr:in-reply-to"

    /**
     * An answer's links, all optional: not every system links both ways. The request it answers, which the reply
     * keeps from the first answer on, if it is named; and any other link, said in so many words.
     */
    private const val LINKS = """"_links": {"type": "object", "additionalProperties": true, """ +
        """"properties": {"$IN_REPLY_TO": {"type": "object", """ +
        """"required": ["href"], "properties": {"href": {"type": "string", "format": "uri"}, """ +
        """"type": {"type": "string"}}}}}"""

    private const val PROGRESS_INPUT = """{"type": "object", "required": ["state", "percent"], "properties": {""" +
        """"state": {"const": "awaiting"}, "percent": {"type": "integer", "minimum": 0, "maximum": 100}, """ +
        """"note": {"type": ["string", "null"], "default": null}, $LINKS}}"""
    private const val FAILED_INPUT = """{"type": "object", "required": ["state", "type", "title"], "properties": {""" +
        """"state": {"const": "failed"}, "type": {"type": "string", "format": "uri"}, "title": {"type": "string"}, """ +
        """"detail": {"type": "string"}, $LINKS}}"""

    /** The result form's schema: the shape the reply's owner names for the [result] it wants. */
    private fun completedInput(result: String) =
        """{"type": "object", "required": ["state", "result"], "properties": {"state": {"const": "completed"}, """ +
            """"result": $result, "note": {"type": ["string", "null"]}, $LINKS}}"""

    /** RISE's vocabulary, named `hr:`: what a reply takes, and the cancelling of the request it answers. */
    val PROFILE = Profile(
        name = "hr",
        title = "H.E.A.R.T. RISE, a reply",
        doc = "A caller's reply: the answers to its request, while it awaits them.",
        relations = listOf(
            Relation(
                "progress", AlpsType.UNSAFE, "Report progress",
                "Tells the caller how far the work has got.",
                "Offered while the reply is awaiting. A JSON Merge Patch of the reply: a note left out stays, a " +
                    "null note is cleared.",
            ),
            Relation(
                "completed", AlpsType.UNSAFE, "Report completed",
                "Sends the result: the work is done.",
                "Offered while the reply is awaiting; the reply is answered after, at 100 percent. The same result " +
                    "again is answered as the first was; a different one is not offered.",
            ),
            Relation(
                "failed", AlpsType.UNSAFE, "Report failed",
                "Says the work failed, with a problem document.",
                "Offered while the reply is awaiting; the reply is answered after, and keeps the problem.",
            ),
            // Not a reply's: the request's own, which a service that takes requests borrows (Profile.borrowed).
            Relation(
                "cancel", AlpsType.UNSAFE, "Cancel the request",
                "Withdraws a request before it is answered: no answer will come.",
                "Offered while the request awaits its answer; after it, the request says why it can't be cancelled.",
            ),
            // The links a request and its answers carry: safe to follow, never acted on.
            Relation(
                "reply-to", AlpsType.SAFE, "Reply to",
                "In a request's envelope: the caller's own reply, where the service answers it.",
                "On a request, and on the watch it brings into being.",
            ),
            Relation(
                "keys", AlpsType.SAFE, "Keys",
                "The JWK Set of keys the linking party signs with, by key id.",
                "On a service's index and its watches, and in a request's envelope; read with no credential.",
            ),
            Relation(
                "in-reply-to", AlpsType.SAFE, "In reply to",
                "From an answer, and the reply it lands on, to the request it answers: its watch.",
                "In every answer's links; the reply keeps the first one, and refuses another.",
            ),
        ),
        vocabulary = "$HEART_RISE_PROFILE/rels/",
    )

    private fun type(result: String, links: (Reply) -> Map<String, Any?>) = ResourceType(
        collection = "replies",
        profile = PROFILE,
        acts = listOf(
            Act("progress", "progress", PROGRESS_INPUT, method = Act.PATCH, atItem = true),
            Act("completed", "completed", completedInput(result), method = Act.PATCH, atItem = true),
            Act("failed", "failed", FAILED_INPUT, method = Act.PATCH, atItem = true, bodyType = PROBLEM),
        ),
        offers = Offers(
            keys = listOf(Reply.AWAITING, Reply.COMPLETED, Reply.FAILED),
            offered = { standing ->
                if (standing == Reply.AWAITING) listOf("progress", "completed", "failed") else emptyList()
            },
            whyAbsent = { standing, _ -> "The reply is $standing, so it takes no more." },
        ),
        state = StateView<Reply, String>(
            keyOf = { it.standing },
            idOf = { it.number },
            versionOf = { it.version },
            bodyOf = { reply ->
                linkedMapOf(
                    "reply" to reply.reference,
                    "state" to reply.standing,
                    "percent" to reply.percent,
                    "note" to reply.note,
                    "result" to reply.result,
                ).filterValues { it != null }
            },
            keyField = "state",
            itemPath = ItemPath("/replies/{reply}", mapOf("reply" to "/reply")),
            // The request the reply answers, from its first answer on; and a failure's problem, embedded.
            hypermediaOf = { reply ->
                Hypermedia(
                    links = listOfNotNull(reply.inReplyTo?.let { IN_REPLY_TO to mapOf("href" to it) }).toMap() +
                        links(reply),
                    embedded = listOfNotNull(reply.problem?.let { "problem" to it }).toMap(),
                )
            },
        ),
        replays = { current, act, body -> repeats(current, act, body) },
    )

    /**
     * The reply resource over [store], whose owner wants a [result] of this shape (a JSON Schema): each act's state
     * is replaced in it, or replayed when it repeats. [links] are the owner's own links on each reply, such as
     * its history, which the owner keeps.
     */
    fun resource(
        store: ReplyStore,
        result: String = ANY_RESULT,
        links: (Reply) -> Map<String, Any?> = { emptyMap() },
    ): HeartResource<Reply, String> = HeartResource(
        layers = Layers(type(result, links)),
        loadAt = { variables ->
            store.get(variables.getValue("reply"))
                ?: throw ResourceNotFoundException("no reply ${variables["reply"]}")
        },
        perform = { current, act, input ->
            if (repeats(current, act, mapper.writeValueAsString(input.json))) {
                Acted(current)
            } else {
                Acted(store.replace(current, next(current, act, input.json)))
            }
        },
    )

    /**
     * Whether [act] with [body] repeats the result the reply has: the same completed result, sent again, changes
     * nothing (decision 8: the repeat is answered as the first was). A different result is not a repeat.
     */
    private fun repeats(current: Reply, act: String, body: String?): Boolean {
        if (act != "completed" || current.standing != Reply.COMPLETED || body == null) return false
        return mapper.readTree(body)["result"]?.let { mapper.convertValue(it, PLAIN) } == current.result
    }

    /**
     * The state after [act] with its checked [patch]: progress is merged and keeps it awaiting; a result completes
     * it at 100 percent, with no note unless it sends one; a failure fails it, keeping the problem it was sent as.
     */
    private fun next(current: Reply, act: String, patch: JsonNode): Reply =
        answering(current, patch).let { kept -> stated(kept, act, patch) }

    /**
     * [current], keeping the request [patch] names it answers: the first answer's, from then on. An answer that
     * names another request is refused, and changes nothing.
     */
    private fun answering(current: Reply, patch: JsonNode): Reply {
        val named = patch.path("_links").path(IN_REPLY_TO).path("href").takeUnless { it.isMissingNode }?.asText()
        return when {
            named == null || named == current.inReplyTo -> current
            current.inReplyTo == null -> current.copy(inReplyTo = named)
            else -> throw LinkConflictException(
                IN_REPLY_TO,
                "reply ${current.reference} answers ${current.inReplyTo}, not $named",
            )
        }
    }

    private fun stated(current: Reply, act: String, patch: JsonNode): Reply = when (act) {
        "progress" -> current.copy(
            percent = patch["percent"].asInt(),
            note = if (patch.has("note")) text(patch["note"]) else current.note,
            version = current.version + 1,
        )
        "completed" -> current.copy(
            standing = Reply.COMPLETED,
            percent = FULL,
            note = text(patch["note"]),
            result = mapper.convertValue(patch["result"], PLAIN),
            version = current.version + 1,
        )
        else -> current.copy(
            standing = Reply.FAILED,
            // The problem as it was sent, without what made it an act: its state and its links.
            problem = mapper.convertValue<Map<String, Any?>>(patch, PLAIN) - ItemActs.STATE - "_links",
            version = current.version + 1,
        )
    }

    private fun text(node: JsonNode?): String? = node?.takeUnless { it.isNull }?.asText()
}
