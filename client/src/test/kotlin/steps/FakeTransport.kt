package steps

import heart.ServerResponse
import heart.Transport
import kotlinx.serialization.json.JsonObject

class FakeTransport : Transport {
    private val queue = ArrayDeque<ServerResponse>()

    /** Every submit() call, in order, so steps can assert the network was
     *  (or was not) touched. An empty queue also throws, so "an error was
     *  raised" alone can't tell a client-side refusal from a network call. */
    val submits = mutableListOf<String>()

    fun enqueue(body: String, contentType: String) {
        queue.addLast(ServerResponse(body, contentType))
    }

    override fun get(url: String): ServerResponse = queue.removeFirst()

    override fun submit(url: String, method: String, payload: JsonObject): ServerResponse {
        submits += "$method $url"
        return queue.removeFirst()
    }
}
