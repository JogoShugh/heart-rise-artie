package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The HTTP binding (SPEC §6.0 table, §6.1–§6.12): the core's read, fetch and act
 * as HTTP requests over a [Transport], and HTTP responses as core outcomes. Every
 * header and status code ARTIE knows about lives here.
 */
class HttpBinding(private val transport: Transport) : Binding {

    override fun read(url: String, rendering: Rendering): Outcome =
        // SPEC §6.3: Layer 1 is never fresh. The request directive binds every cache
        // on the path (RFC 9111 §5.2.1.4), whatever the stored response said.
        outcome(transport.send(HeartRequest("GET", url, negotiation(rendering) + LAYER1_NO_CACHE)))

    override fun fetch(url: String, accept: List<String>, integrity: String?): Outcome {
        val headers = mapOf("Accept" to accept.joinToString(", "))
        return outcome(transport.send(HeartRequest("GET", url, headers, integrity = integrity)))
    }

    override fun act(act: Act, rendering: Rendering): Outcome {
        val headers = negotiation(rendering) + mapOf("Content-Type" to act.contentType, "If-Match" to act.validator)
        return outcome(transport.send(HeartRequest(act.method, act.url, headers, act.body)))
    }

    private fun outcome(response: HeartResponse): Outcome {
        val code = response.status.toString()
        val problem = problemType(response)
        return when (response.status) {
            in SUCCESS -> Outcome.Done(received(response), code)
            PRECONDITION_REQUIRED -> Outcome.PreconditionRequired(code, problem)
            PRECONDITION_FAILED -> Outcome.Stale(code, problem)
            CONFLICT -> Outcome.NotOffered(code, problem)
            UNPROCESSABLE -> Outcome.Invalid(violations(response), code, problem)
            else -> Outcome.Failed(code, problem)
        }
    }

    private companion object {
        val LAYER1_NO_CACHE = mapOf("Cache-Control" to "no-cache")
        private val profileLink = Regex("""<([^>]*)>\s*;\s*rel="?profile"?""")

        val SUCCESS = 200..299
        const val CONFLICT = 409
        const val PRECONDITION_FAILED = 412
        const val UNPROCESSABLE = 422
        const val PRECONDITION_REQUIRED = 428

        /**
         * The headers that select a representation: sent on every read and every
         * act. HTTP is stateless, so an act repeats them; the server then selects
         * the same representation and compares If-Match with its ETag, whole
         * (RFC 9110 §13.1.1).
         */
        fun negotiation(rendering: Rendering): Map<String, String> = buildMap {
            put("Accept", rendering.dialect.mediaType)
            put("Accept-Profile", "<${rendering.dialect.profile}>")
            rendering.rung.prefer?.let { put("Prefer", it) }
        }

        /** SPEC §6.1, §6.11.1: the profile from `Link: rel="profile"`, the rung from Preference-Applied. */
        fun received(response: HeartResponse): Received {
            val preferenceApplied = response.header("Preference-Applied")
            return Received(
                bytes = response.bytes,
                mediaType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(),
                profiles = profileLink.findAll(response.header("Link").orEmpty()).map { it.groupValues[1] }.toList(),
                validator = response.header("ETag"),
                applied = Rung.applied(preferenceApplied),
                appliedAs = preferenceApplied,
            )
        }

        fun problemType(response: HeartResponse): String? =
            runCatching { Json.parseToJsonElement(response.body).jsonObject["type"]?.jsonPrimitive?.content }
                .getOrNull()

        /** The `violations` of a 422 body (RFC 9457, SPEC §6.2), or its `error` text, or nothing. */
        fun violations(response: HeartResponse): List<String> {
            val body = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull()
                ?: return emptyList()
            // Each violation is {pointer, message}. An older server sent strings.
            val listed = (body["violations"] as? JsonArray)?.map { item ->
                (item as? JsonObject)?.let { pointerMessage(it) } ?: item.jsonPrimitive.content
            }
            return listed ?: listOfNotNull(body["error"]?.jsonPrimitive?.content)
        }

        private fun pointerMessage(violation: JsonObject): String =
            "${violation["pointer"]?.jsonPrimitive?.content}: ${violation["message"]?.jsonPrimitive?.content}"
    }
}
