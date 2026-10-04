package steps

import heart.ArtieClient
import heart.HeartRepresentation
import heart.HeartRiseClient

class ClientContext {
    val transport = FakeTransport()
    val client: HeartRiseClient = ArtieClient(transport)
    lateinit var current: HeartRepresentation
    var contentType: String = ""
    var enterBody: String = ""
    var actBody: String = ""
    var lastError: Throwable? = null

    fun enqueueEnterResponse() = transport.enqueue(enterBody, contentType)
    fun enqueueActResponse() = transport.enqueue(actBody, contentType)
}
