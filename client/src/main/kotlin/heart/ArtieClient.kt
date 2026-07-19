package heart

import kotlinx.serialization.json.JsonObject

class ArtieClient(private val transport: Transport) : HeartRiseClient {
    private var current: HeartRepresentation? = null

    override fun enter(url: String): HeartRepresentation {
        val (body, contentType) = transport.get(url)
        return HeartResponseParser.parse(body, contentType).also { current = it }
    }

    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        val form = current?.affordance(rel) ?: error("No affordance '$rel' in current manifest")
        val (body, contentType) = transport.submit(form.href, form.method, payload)
        return HeartResponseParser.parse(body, contentType).also { current = it }
    }
}
