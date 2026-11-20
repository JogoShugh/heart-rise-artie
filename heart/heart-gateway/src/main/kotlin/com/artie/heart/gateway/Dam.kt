package com.artie.heart.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * A rung's refusal, before the upstream is asked: problem+json, with the upstream's own `message` member beside
 * the detail, so a client written for GitHub reads it as it reads GitHub's.
 */
internal fun damRefusal(status: Status, detail: String): Response {
    val body = mapOf("title" to status.description, "status" to status.code, "detail" to detail, "message" to detail)
    return Response(status).header("Content-Type", "application/problem+json")
        .body(ObjectMapper().writeValueAsString(body))
}

/**
 * The controls the gateway's owner puts in front of the upstream for a client that passes through
 * (spec/ideas/gateway-pass-through.md, GATEWAY_DAM): none, the transparent rung, unless the owner names some. The
 * owner chooses them, never the caller.
 */
class Dam(val rungs: Set<Rung>) {
    /** One control, by the word GATEWAY_DAM names it with. */
    enum class Rung(val word: String) {
        DESCRIBED("described"),
        CONNECTOR("connector"),
        OFFERED("offered"),
        CONDITIONAL("conditional"),
    }

    operator fun contains(rung: Rung) = rung in rungs

    /** The rungs in the order a request meets them, as GATEWAY_DAM names them. */
    override fun toString() = rungs.sortedBy { it.ordinal }.joinToString(", ") { it.word }

    companion object {
        val NONE = Dam(emptySet())

        /** The rungs [words] names, separated by commas: `conditional`, `described,connector`. */
        fun parse(words: String): Dam = Dam(
            words.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { word ->
                requireNotNull(Rung.entries.firstOrNull { it.word == word }) {
                    "names no rung $word; the rungs are ${Rung.entries.joinToString(", ") { it.word }}"
                }
            }.toSet(),
        )
    }
}
