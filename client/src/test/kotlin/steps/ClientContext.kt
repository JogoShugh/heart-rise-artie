package steps

import heart.ArtieClient
import heart.Binding
import heart.CachingTransport
import heart.EventLog
import heart.EventSink
import heart.EventSinks
import heart.IntegrityCheck
import heart.Dialect
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.HttpTransport
import heart.Rung
import heart.Trace
import heart.TraceContextTransport
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
    val client: HeartRiseClient by lazy { build("first", cache, rung, dialect) }

    /** When true, the second client sits behind the first client's cache, as two
     *  clients behind one shared cache do in SPEC §6.10.4's diagram. */
    var shareCache = false

    /** Another client: a concurrent writer, or a neighbour behind a shared cache. */
    val second: HeartRiseClient by lazy {
        val transport = if (shareCache) cache else CachingTransport(IntegrityCheck(wire))
        build("second", transport, rung, dialect)
    }

    lateinit var current: HeartRepresentation
    var lastError: Throwable? = null

    /** A fresh client with its own cache, on the same wire. */
    fun newClient(dialect: Dialect, rung: Rung = Rung.L0): HeartRiseClient =
        build("client-${++clients}", CachingTransport(IntegrityCheck(wire)), rung, dialect)

    /** Where each client's event log goes: off unless ARTIE_EVENTS or ARTIE_EVENTS_URL
     *  is set, or a scenario sets it. */
    var eventSink: EventSink = EventSinks.fromEnvironment(System.getenv(), HttpTransport())

    /** Every client built in this scenario, so each can say it is done when the scenario ends. */
    val created = mutableListOf<HeartRiseClient>()

    /** Names the scenario in each client's User-Agent; LiveSteps sets it from the scenario name. */
    var scenario = "scenario"
    private var clients = 0

    /**
     * Composes a client as production would: it names itself and carries its
     * trace context outermost, above its own cache, so a second client behind
     * a shared cache keeps its own name and trace on the wire. Its event log
     * shares the trace, so each event points at the request it is about.
     */
    private fun build(who: String, next: Transport, rung: Rung, dialect: Dialect): HeartRiseClient {
        val agent = "${UserAgentTransport.DEFAULT} ($scenario; $who)"
        val trace = Trace()
        val transport = UserAgentTransport(agent, TraceContextTransport(trace, next))
        val events = EventLog(eventSink, trace, agent)
        return ArtieClient(transport, rung, dialect, allowUncheckedLinks, events, bindings).also { created += it }
    }

    /** Bindings for schemes other than HTTP's, by scheme (SPEC §6.0); a scenario adds them before entering. */
    val bindings = mutableMapOf<String, Binding>()

    /** How many requests the client had sent when it finished entering. */
    var requestsAfterEnter = 0

    /** Addresses and integrity values minted by the scenario, by name:
     *  "set:planted" -> "/affordance-sets/<16 hex>",
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
        val ADDRESS = Regex("""\{((?:integrity:)?(?:set|schema|unavailable):[^}]+)}""")
    }
}
