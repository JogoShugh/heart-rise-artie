package steps

import heart.HeartRequest
import heart.HeartResponse
import heart.Transport

/**
 * Scripted server. Each `respond` call queues one response for a method and
 * URL. Every request is recorded, so steps can check what the client sent,
 * and that it sent nothing it should not have. A request with no scripted
 * response throws, so a missing answer cannot pass silently.
 */
class FakeTransport : Transport {
    val requests = mutableListOf<HeartRequest>()
    private val scripted = mutableMapOf<String, ArrayDeque<HeartResponse>>()

    fun respond(method: String, url: String, response: HeartResponse) {
        scripted.getOrPut("$method $url") { ArrayDeque() }.addLast(response)
    }

    override fun send(request: HeartRequest): HeartResponse {
        requests += request
        val key = "${request.method} ${request.url}"
        return scripted[key]?.removeFirstOrNull() ?: error("no scripted response for $key")
    }
}
