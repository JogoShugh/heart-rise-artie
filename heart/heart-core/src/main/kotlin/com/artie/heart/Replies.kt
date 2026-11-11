package com.artie.heart

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Ana's reply: a caller's own resource, where the answers to its request land (RISE, spec/ideas/rise-deep-dive.md).
 * Its reference is the name the request gave it, such as `r-7f3`; its number is that name's hexadecimal part.
 */
data class Reply(
    val reference: String,
    val standing: String = AWAITING,
    val percent: Int? = null,
    val note: String? = null,
    val result: Map<String, Any?>? = null,
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
 * The reply resource. While it awaits, it takes progress, then one answer: its result, or a failure. A repeat of the
 * result it has is replayed (SPEC §6.2, decision 8); any other act on an answered reply is not offered.
 */
object Replies {
    private val mapper = ObjectMapper()

    /** A result as plain values, which heart-core canonicalises for the validator (JCS). */
    private val RESULT = object : TypeReference<Map<String, Any?>>() {}

    private const val PROGRESS_INPUT = """{"type": "object", "required": ["percent", "note"], "properties": {""" +
        """"percent": {"type": "integer", "minimum": 0, "maximum": 100}, """ +
        """"note": {"type": ["string", "null"]}}}"""
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
                "Offered while the reply is awaiting. A PUT replaces the whole progress, percent and note; a repeat " +
                    "writes the same progress again.",
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
        vocabulary = "$HEART_RISE_PROFILE/rels/",
    )

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
        ),
        replays = { current, act, body -> repeats(current, act, body) },
    )

    /** The reply resource over [store]: each act's state is replaced in it, or replayed when it repeats. */
    fun resource(store: ReplyStore): HeartResource<Reply, String> = HeartResource(
        layers = Layers(type),
        loadAt = { variables ->
            store.get(variables.getValue("reply"))
                ?: throw ResourceNotFoundException("no reply ${variables["reply"]}")
        },
        perform = { current, act, input ->
            if (repeats(current, act, mapper.writeValueAsString(input.json))) {
                Acted(current)
            } else {
                Acted(store.replace(current, next(current, act, input)))
            }
        },
    )

    /**
     * Whether [act] with [body] repeats the result the reply has: the same completed result, sent again, changes
     * nothing (decision 8: the repeat is answered as the first was). A different result is not a repeat.
     */
    private fun repeats(current: Reply, act: String, body: String?): Boolean {
        if (act != "completed" || current.standing != Reply.COMPLETED || body == null) return false
        return mapper.readTree(body)["result"]?.let { mapper.convertValue(it, RESULT) } == current.result
    }

    /** The state after [act] with its checked [input]: progress keeps it awaiting; a result or failure answers it. */
    private fun next(current: Reply, act: String, input: ActInput): Reply = when (act) {
        "progress" -> current.copy(
            percent = input.json["percent"].asInt(),
            note = input.json["note"]?.takeUnless { it.isNull }?.asText(),
            version = current.version + 1,
        )
        "completed" -> current.copy(
            standing = Reply.COMPLETED,
            result = mapper.convertValue(input.json["result"], RESULT),
            version = current.version + 1,
        )
        else -> current.copy(standing = Reply.FAILED, version = current.version + 1)
    }
}
