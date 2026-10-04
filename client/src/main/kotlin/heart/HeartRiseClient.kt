package heart

import kotlinx.serialization.json.JsonObject

interface HeartRiseClient {
    /** The last synced resource, including after a refusal's re-sync. Null before enter(). */
    val current: HeartRepresentation?

    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation
}
