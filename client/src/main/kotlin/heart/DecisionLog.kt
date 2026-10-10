package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The decisions ArtieClient reports to its [EventLog], one function per
 * kind, so the client's own code stays about HTTP. Each names the URL of
 * the request the decision was about; the log ties it to that request's span.
 */
internal class DecisionLog(private val events: EventLog) {

    /** SPEC §6.11.1: the rung a response is at, from Preference-Applied. */
    fun rung(url: String, preferenceApplied: String?, applied: Rung) =
        events.emit(
            "rung.applied", "took rung $applied from Preference-Applied", url,
            mapOf("heart.preference_applied" to preferenceApplied.orEmpty(), "heart.rung" to applied.name),
        )

    /** Whether the forms link was followed, and why not when it was not. */
    fun forms(url: String, href: String?, applied: Rung) = when {
        href == null && applied != Rung.L0 -> events.emit(
            "forms.missing", "the response is at $applied but links no affordance set it can name", url,
            mapOf("heart.rung" to applied.name),
        )
        href == null -> Unit
        applied == Rung.L0 -> events.emit(
            "forms.skipped", "did not follow the forms link: no Preference-Applied means L0", url,
            mapOf("heart.forms.href" to href, "heart.rung" to applied.name),
        )
        else -> events.emit(
            "forms.followed", "followed the forms link at $applied", url,
            mapOf("heart.forms.href" to href, "heart.rung" to applied.name),
        )
    }

    /** HAL-SCHEMA-FORMS-HR Rule 2: bytes refused, never parsed or cached. */
    fun integrityRefused(url: String, expected: String?, reason: String?) =
        events.emit(
            "integrity.refused", "refused the response: ${reason ?: "integrity check failed"}", url,
            mapOf("heart.integrity.expected" to expected),
        )

    fun integrityChecked(url: String, expected: String?) =
        events.emit(
            "integrity.ok", "the bytes match the link's integrity", url,
            mapOf("heart.integrity.expected" to expected),
        )

    /** An act sent, with the precondition it carried and the resource it was derived from. */
    fun actSent(rel: String, request: HeartRequest, owner: String, status: Int) =
        events.emit(
            "act.sent", "sent $rel with If-Match ${request.header("If-Match")}", request.url,
            mapOf(
                "heart.rel" to rel,
                "http.request.method" to request.method,
                "heart.if_match" to request.header("If-Match"),
                "heart.resource" to owner,
                "http.response.status_code" to status.toString(),
            ),
        )

    /** SPEC §6.2, §6.3: a refusal, its problem type, and what ARTIE did about it. */
    fun actRefused(rel: String, target: String, response: HeartResponse, outcome: String) =
        events.emit(
            "act.refused", "$rel was refused (${response.status}); $outcome", target,
            mapOf(
                "heart.rel" to rel,
                "http.response.status_code" to response.status.toString(),
                "heart.problem.type" to problemType(response),
                "heart.outcome" to outcome,
            ),
        )

    /** An act or link the representation does not offer: refused before any request. */
    fun absent(kind: String, rel: String, url: String) =
        events.emit("$kind.absent", "$rel is not offered in $url; nothing was sent", url, mapOf("heart.rel" to rel))

    fun done() = events.emit("client.done", "the client has finished", null)

    private fun problemType(response: HeartResponse): String? =
        runCatching { Json.parseToJsonElement(response.body).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull()
}
