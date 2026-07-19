package heart

import kotlinx.serialization.json.JsonObject

interface HeartRiseClient {
    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation
}
