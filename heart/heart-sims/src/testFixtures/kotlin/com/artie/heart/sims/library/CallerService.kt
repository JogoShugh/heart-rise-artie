package com.artie.heart.sims.library

import com.artie.heart.Act
import com.artie.heart.ActInput
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import org.http4k.core.HttpHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Ana's reply: the caller's own resource, where the answers to her request land (tell-me-when-renewable.feature).
 * Its reference is the name the request gave it, such as `r-7f3`; its number is that name's hexadecimal part.
 */
data class Reply(
    val reference: String,
    val standing: String = AWAITING,
    val percent: Int? = null,
    val note: String? = null,
    val version: Int = 1,
) {
    val number: Long get() = reference.removePrefix("r-").toLong(16)

    companion object {
        const val AWAITING = "awaiting"
        const val COMPLETED = "completed"
        const val FAILED = "failed"
    }
}

/**
 * Ana's side of the exchange: a HEART service of replies, each awaiting until the library answers it. Only the library,
 * with its Bearer token, may act on a reply.
 */
class CallerService {
    private val store = ConcurrentHashMap<String, Reply>()

    /** Opens the reply named [reference], awaiting. */
    fun open(reference: String) {
        store[reference] = Reply(reference)
    }

    /** Whether a reply named [reference] is open. */
    fun has(reference: String): Boolean = store.containsKey(reference)

    private fun reply(reference: String): Reply? = store[reference]

    /** [next] in place of [current], only if nothing else changed the reply since; a stale write otherwise. */
    private fun replace(current: Reply, next: Reply): Reply {
        if (!store.replace(current.reference, current, next)) {
            throw ConcurrentWriteException("reply ${current.reference} changed since it was read")
        }
        return next
    }

    /** The state after [act], with its checked [input]: progress keeps the reply awaiting; a result or a failure answers it. */
    private fun next(current: Reply, act: String, input: ActInput): Reply = when (act) {
        "progress" -> current.copy(
            percent = input.json["percent"].asInt(),
            note = input.json["note"]?.asText(),
            version = current.version + 1,
        )
        "completed" -> current.copy(standing = Reply.COMPLETED, version = current.version + 1)
        else -> current.copy(standing = Reply.FAILED, version = current.version + 1)
    }

    private val type = ResourceType(
        collection = "replies",
        profile = PROFILE,
        acts = listOf(
            Act("progress", "progress", PROGRESS_INPUT, method = Act.PUT),
            Act("completed", "completed", COMPLETED_INPUT, method = Act.PUT),
            Act("failed", "failed", FAILED_INPUT, method = Act.PUT),
        ),
        offers = Offers(
            keys = listOf(Reply.AWAITING, Reply.COMPLETED, Reply.FAILED),
            offered = { standing -> if (standing == Reply.AWAITING) listOf("progress", "completed", "failed") else emptyList() },
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
                ).filterValues { it != null }
            },
            keyField = "state",
            itemPath = ItemPath("/replies/{reply}", mapOf("reply" to "/reply")),
        ),
    )

    private val replies = HeartResource(
        layers = Layers(type),
        loadAt = { variables ->
            reply(variables.getValue("reply")) ?: throw ResourceNotFoundException("no reply ${variables["reply"]}")
        },
        perform = { current, act, input -> Acted(replace(current, next(current, act, input))) },
    )

    /** The caller's HEART service: the replies, served to the library's Bearer token. */
    val handler: HttpHandler = HeartHttp(
        listOf(replies),
        Credentials.parse(users = "", tokens = Tokens.sha256(LIBRARY_TOKEN)),
        HeartSettings(title = "Ana's replies", realm = PROFILE.name),
    )

    companion object {
        /** The Bearer token the library presents to Ana's replies. */
        const val LIBRARY_TOKEN = "riverside-library-reply-token"

        private const val PROGRESS_INPUT = """{"type": "object", "required": ["percent"], "properties": {""" +
            """"percent": {"type": "integer", "minimum": 0, "maximum": 100}, """ +
            """"note": {"type": "string"}}}"""
        private const val COMPLETED_INPUT = """{"type": "object", "required": ["result"], "properties": {""" +
            """"result": {"type": "object"}}}"""
        private const val FAILED_INPUT = """{"type": "object", "required": ["title"], "properties": {""" +
            """"title": {"type": "string"}, "detail": {"type": "string"}}}"""

        /** The profile of the reply: RISE's relations for what a reply takes, named `hr:`. */
        val PROFILE = Profile(
            name = "hr",
            title = "H.E.A.R.T. RISE, a reply",
            doc = "A caller's reply: the answers to its request, while it awaits them.",
            relations = listOf(
                Relation(
                    "progress", AlpsType.IDEMPOTENT, "Report progress",
                    "Tells the caller how far the work has got.",
                    "Offered while the reply is awaiting. A PUT replaces the progress; a repeat changes nothing.",
                ),
                Relation(
                    "completed", AlpsType.IDEMPOTENT, "Report completed",
                    "Sends the result: the work is done.",
                    "Offered while the reply is awaiting; the reply is answered after.",
                ),
                Relation(
                    "failed", AlpsType.IDEMPOTENT, "Report failed",
                    "Says the work failed, with a problem document.",
                    "Offered while the reply is awaiting; the reply is answered after.",
                ),
            ),
        )
    }
}
