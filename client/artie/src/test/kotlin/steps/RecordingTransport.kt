package steps

import heart.HeartRequest
import heart.HeartResponse
import heart.Transport

/** Records every request that reaches the wire, and its status, for live scenarios. */
class RecordingTransport(private val inner: Transport) : Transport {
    val exchanges = mutableListOf<Pair<HeartRequest, Int>>()

    override fun send(request: HeartRequest): HeartResponse =
        inner.send(request).also { exchanges += request to it.status }
}
