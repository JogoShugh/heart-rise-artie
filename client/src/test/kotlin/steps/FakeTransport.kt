package steps

import heart.ServerResponse
import heart.Transport
import kotlinx.serialization.json.JsonObject

class FakeTransport : Transport {
    private val queue = ArrayDeque<ServerResponse>()

    fun enqueue(body: String, contentType: String) {
        queue.addLast(ServerResponse(body, contentType))
    }

    override fun get(url: String): ServerResponse = queue.removeFirst()

    override fun submit(url: String, method: String, payload: JsonObject): ServerResponse =
        queue.removeFirst()
}
