package heart

import kotlinx.serialization.json.JsonObject
import java.net.URI

/**
 * ARTIE's deterministic core: everything between a selection (an
 * affordance name and field values) and the wire (SPEC §6.8). It never
 * builds a URI of its own; targets come from the manifest, and templates
 * are expanded from Layer 1 by the one [0.5] rule (see UriTemplate).
 */
class ArtieClient(private val transport: Transport) : HeartRiseClient {
    override var current: HeartRepresentation? = null
        private set

    override fun enter(url: String): HeartRepresentation = sync(url)

    override fun act(rel: String, payload: JsonObject): HeartRepresentation = act(rel, payload, resendAllowed = true)

    /**
     * One act and its refusal handling (SPEC §6.3). 428 is the only refusal
     * that resends, and only once: the client always sends If-Match, so a
     * second 428 would mean something else is wrong.
     */
    private fun act(rel: String, payload: JsonObject, resendAllowed: Boolean): HeartRepresentation {
        val held = current ?: error("enter a service before acting")
        val form = held.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val etag = held.etag ?: error("no ETag held for ${held.url}; an act needs If-Match")
        val target = targetOf(form, held)
        val response = transport.send(
            HeartRequest(
                form.method,
                target,
                mapOf("Content-Type" to form.contentType, "Accept" to HAL_JSON, "If-Match" to etag),
                payload.toString(),
            ),
        )
        if (response.status in SUCCESS) {
            return HeartResponseParser.parse(response, target).also { current = it }
        }
        if (response.status == PRECONDITION_REQUIRED && resendAllowed) {
            sync(held.url)
            return act(rel, payload, resendAllowed = false)
        }
        throw refusal(rel, response, held)
    }

    /** 412 and 409 re-sync first, so the client never keeps a manifest the server has refused. */
    private fun refusal(rel: String, response: HeartResponse, held: HeartRepresentation): ActRefused =
        when (response.status) {
            PRECONDITION_FAILED -> StaleAct(rel).also { sync(held.url) }
            CONFLICT -> IllegalTransition(rel).also { sync(held.url) }
            UNPROCESSABLE -> InvalidPayload(rel, HeartResponseParser.violations(response))
            else -> UnexpectedResponse(rel, response.status)
        }

    private fun targetOf(form: AffordanceForm, held: HeartRepresentation): String {
        if (!form.templated) return form.target
        return URI(held.url).resolve(UriTemplate.expand(form.target, held.state)).toString()
    }

    private fun sync(url: String): HeartRepresentation {
        val request = HeartRequest(
            "GET",
            url,
            mapOf("Accept" to HAL_JSON, "Accept-Profile" to "<$HAL_SCHEMA_FORMS_PROFILE>"),
        )
        return HeartResponseParser.parse(transport.send(request), url).also { current = it }
    }

    private companion object {
        val SUCCESS = 200..299
        const val CONFLICT = 409
        const val PRECONDITION_FAILED = 412
        const val UNPROCESSABLE = 422
        const val PRECONDITION_REQUIRED = 428
    }
}
