package steps

import heart.ArtieClient
import heart.CachingTransport
import heart.IntegrityCheck
import heart.Dialect
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.HttpTransport
import heart.Rung
import heart.Transport
import heart.UserAgentTransport

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
    var dialect: Dialect = Dialect.HAL_SCHEMA_FORMS

    /** True only for scenarios about older servers that send no integrity values. */
    var allowUncheckedLinks = false

    /** Everything that reached the wire (below each client's cache), live or fake. */
    val wireLog: RecordingTransport by lazy { RecordingTransport(if (baseUrl != null) HttpTransport() else transport) }
    private val wire: Transport get() = wireLog
    val cache: CachingTransport by lazy { CachingTransport(IntegrityCheck(wire)) }
    val client: HeartRiseClient by lazy { ArtieClient(named("first", cache), rung, dialect, allowUncheckedLinks) }

    /** When true, the second client sits behind the first client's cache, as two
     *  clients behind one shared cache do in SPEC §6.10.4's diagram. */
    var shareCache = false

    /** Another client: a concurrent writer, or a neighbour behind a shared cache. */
    val second: HeartRiseClient by lazy {
        val transport = if (shareCache) cache else CachingTransport(IntegrityCheck(wire))
        ArtieClient(named("second", transport), rung, dialect, allowUncheckedLinks)
    }

    lateinit var current: HeartRepresentation
    var lastError: Throwable? = null

    /** A fresh client with its own cache, on the same wire. */
    fun newClient(dialect: Dialect, rung: Rung = Rung.L0): HeartRiseClient =
        ArtieClient(
            named("client-${++clients}", CachingTransport(IntegrityCheck(wire))),
            rung, dialect, allowUncheckedLinks,
        )

    /** Names the scenario in each client's User-Agent; LiveSteps sets it from the scenario name. */
    var scenario = "scenario"
    private var clients = 0

    /** Each client names itself outermost, above its own cache, so a second
     *  client behind a shared cache keeps its own name on the wire. */
    private fun named(who: String, next: Transport): Transport =
        UserAgentTransport("${UserAgentTransport.DEFAULT} ($scenario; $who)", next)

    /** How many requests the client had sent when it finished entering. */
    var requestsAfterEnter = 0

    /** Addresses and integrity values minted by the scenario, by name:
     *  "set:planted" -> "/affordance-sets/sha256-...",
     *  "integrity:set:planted" -> "sha256-<Base64>". */
    val addresses = mutableMapOf<String, String>()

    /** Replaces {set:name} and {schema:name} with the addresses minted so far,
     *  and {integrity:set:name} and {integrity:schema:name} with their integrity values.
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
        val ADDRESS = Regex("""\{((?:integrity:)?(?:set|schema):[^}]+)}""")
    }
}
