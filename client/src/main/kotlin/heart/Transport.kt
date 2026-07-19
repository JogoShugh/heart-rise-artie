package heart

import kotlinx.serialization.json.JsonObject

data class ServerResponse(val body: String, val contentType: String)

interface Transport {
    fun get(url: String): ServerResponse
    fun submit(url: String, method: String, payload: JsonObject): ServerResponse
}
