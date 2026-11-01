package conformance

import com.artie.heart.sims.Simulator
import heart.ArtieClient
import heart.CachingTransport
import heart.Dialect
import heart.EventLog
import heart.EventSink
import heart.HeartRepresentation
import heart.HeartRequest
import heart.HeartResponse
import heart.IntegrityCheck
import heart.Rung
import heart.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import steps.InProcessTransport

/** One client in a scenario: ARTIE, every request it sent, and every line of its event log. */
class Actor(val client: ArtieClient, val log: MutableList<HeartRequest>, val events: MutableList<JsonObject>)

/**
 * What one conformance scenario holds, shared by its step classes: the
 * service the clients talk to, the clients, and what the last step left.
 */
class World {
    lateinit var scenarioName: String
    lateinit var service: Transport
    var bookmark = BOOKMARK
    var me: Actor? = null
    var other: Actor? = null
    var rep: HeartRepresentation? = null
    var error: Exception? = null
    var validatorBefore: String? = null
    var sentBefore = 0

    /** The simulated services, in memory, for this scenario alone. */
    val sims by lazy { Simulator() }

    val actor get() = checkNotNull(me) { "no client yet" }
    val held get() = checkNotNull(rep) { "nothing held" }

    /** The simulated services as the service this scenario's clients talk to. */
    fun simulated() {
        service = InProcessTransport(sims.handler)
    }

    /**
     * ARTIE at [rung] in [dialect], sending [token] on every request it makes, and keeping its event
     * log. Its shared layers are kept in [cache], made over its own wire when none is given; null keeps none.
     */
    fun client(
        rung: String,
        token: String?,
        dialect: Dialect = Dialect.HAL_SCHEMA_FORMS,
        cache: ((Transport) -> Transport)? = { CachingTransport(it) },
    ): Actor {
        val log = mutableListOf<HeartRequest>()
        val events = mutableListOf<JsonObject>()
        val wire = IntegrityCheck(wire(log, token))
        val eventLog = EventLog(EventSink { events += Json.parseToJsonElement(it).jsonObject }, null, "conformance")
        val client = ArtieClient(cache?.invoke(wire) ?: wire, Rung.valueOf(rung), dialect, events = eventLog)
        return Actor(client, log, events)
    }

    /** The service, as one client reaches it: every request kept in [log], [token] added to each. */
    fun wire(log: MutableList<HeartRequest>, token: String?) = object : Transport {
        override fun send(request: HeartRequest): HeartResponse {
            log += request
            val sent = token?.let { request.copy(headers = request.headers + ("Authorization" to it)) }
            return service.send(sent ?: request)
        }
    }

    /** Runs [step], keeping what it throws for a Then to read. */
    fun trying(step: () -> HeartRepresentation) {
        try {
            rep = step()
        } catch (refused: RuntimeException) {
            error = refused
            rep = actor.client.current
        }
    }
}

/** The credential each simulated service admits, by the name the features give it. */
val CREDENTIALS = mapOf("build" to "Bearer build-token", "deploy" to "Bearer deploy-token")
