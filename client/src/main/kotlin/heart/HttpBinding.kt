package heart

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
        val problem = ProblemDetails.type(response.body)
        return when (response.status) {
            in SUCCESS -> Outcome.Done(received(response), code)
            PRECONDITION_REQUIRED -> Outcome.PreconditionRequired(code, problem)
            PRECONDITION_FAILED -> Outcome.Stale(code, problem)
            CONFLICT -> Outcome.NotOffered(code, problem)
            UNPROCESSABLE -> Outcome.Invalid(ProblemDetails.violations(response.body), code, problem)
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
    }
}
