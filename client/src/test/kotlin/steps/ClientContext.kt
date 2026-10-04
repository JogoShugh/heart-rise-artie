package steps

import heart.ArtieClient
import heart.CachingTransport
import heart.ContentAddressCheck
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.HttpTransport
import heart.Rung
import heart.Transport

/**
 * Per-scenario state. Scenarios talk to FakeTransport unless tagged @live,
 * in which case LiveSteps sets [baseUrl] and both clients use real HTTP.
 * Each client is composed as in production: its own HTTP cache, then the
 * content-address check, then the wire. FakeTransport records only what
 * reaches the wire, so a cache hit is a request that does not appear.
 */
class ClientContext {
    val transport = FakeTransport()
    var baseUrl: String? = null
    var rung: Rung = Rung.L0

    /** Everything that reached the wire (below each client's cache), live or fake. */
    val wireLog: RecordingTransport by lazy { RecordingTransport(if (baseUrl != null) HttpTransport() else transport) }
    private val wire: Transport get() = wireLog
    val cache: CachingTransport by lazy { CachingTransport(ContentAddressCheck(wire)) }
    val client: HeartRiseClient by lazy { ArtieClient(cache, rung) }

    /** When true, the second client sits behind the first client's cache, as two
     *  clients behind one shared cache do in SPEC §6.10.4's diagram. */
    var shareCache = false

    /** Another client: a concurrent writer, or a neighbour behind a shared cache. */
    val second: HeartRiseClient by lazy {
        ArtieClient(if (shareCache) cache else CachingTransport(ContentAddressCheck(wire)), rung)
    }

    lateinit var current: HeartRepresentation
    var lastError: Throwable? = null

    /** How many requests the client had sent when it finished entering. */
    var requestsAfterEnter = 0

    /** Content addresses minted by the scenario, by name:
     *  "set:planted" -> "/affordance-sets/sha256-...". */
    val addresses = mutableMapOf<String, String>()

    /** Replaces {set:name} and {schema:name} with the addresses minted so far.
     *  URI templates like {id} are left alone. */
    fun expand(text: String): String =
        ADDRESS.replace(text) { match ->
            addresses[match.groupValues[1]] ?: error("no address minted for ${match.value}")
        }

    /** A path like "/beds/1" is resolved against the live server; absolute URLs pass through. */
    fun url(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("/")) checkNotNull(baseUrl) { "relative URL outside a @live scenario" } + pathOrUrl
        else pathOrUrl

    private companion object {
        val ADDRESS = Regex("""\{((?:set|schema):[^}]+)}""")
    }
}
