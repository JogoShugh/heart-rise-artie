package steps

import heart.ArtieClient
import heart.HeartRepresentation
import heart.HeartRiseClient

class ClientContext {
    val transport = FakeTransport()
    val client: HeartRiseClient = ArtieClient(transport)
    lateinit var current: HeartRepresentation
    var lastError: Throwable? = null

    /** How many requests the client had sent when it finished entering. */
    var requestsAfterEnter = 0
}
