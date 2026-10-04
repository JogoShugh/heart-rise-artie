package heart

import kotlinx.serialization.json.JsonObject

/**
 * ARTIE's deterministic core: everything between a selection (an
 * affordance name and field values) and the wire (SPEC §6.8). It never
 * builds a URI of its own; targets come from the manifest.
 */
class ArtieClient(private val transport: Transport) : HeartRiseClient {
    private var current: HeartRepresentation? = null

    override fun enter(url: String): HeartRepresentation {
        val request = HeartRequest(
            "GET",
            url,
            mapOf("Accept" to HAL_JSON, "Accept-Profile" to "<$HAL_SCHEMA_FORMS_PROFILE>"),
        )
        return HeartResponseParser.parse(transport.send(request), url).also { current = it }
    }

    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        val form = current?.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val headers = mapOf("Content-Type" to form.contentType)
        val request = HeartRequest(form.method, form.target, headers, payload.toString())
        return HeartResponseParser.parse(transport.send(request), form.target).also { current = it }
    }
}
