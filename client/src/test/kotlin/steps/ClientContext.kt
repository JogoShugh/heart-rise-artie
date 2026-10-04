package steps

import heart.ArtieClient
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.HttpTransport
import heart.Transport

/**
 * Per-scenario state. Scenarios talk to FakeTransport unless tagged @live,
 * in which case LiveSteps sets [baseUrl] and both clients use real HTTP.
 */
class ClientContext {
    val transport = FakeTransport()
    var baseUrl: String? = null

    private val wire: Transport by lazy { if (baseUrl != null) HttpTransport() else transport }
    val client: HeartRiseClient by lazy { ArtieClient(wire) }

    /** Another, independent client: a concurrent writer for the same resource. */
    val second: HeartRiseClient by lazy { ArtieClient(wire) }

    lateinit var current: HeartRepresentation
    var lastError: Throwable? = null

    /** How many requests the client had sent when it finished entering. */
    var requestsAfterEnter = 0

    /** A path like "/beds/1" is resolved against the live server; absolute URLs pass through. */
    fun url(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("/")) checkNotNull(baseUrl) { "relative URL outside a @live scenario" } + pathOrUrl
        else pathOrUrl
}
