package conformance

import com.artie.heart.sims.Simulator
import heart.ArtieClient
import heart.CachingTransport
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

    /** ARTIE at [rung], sending [token] on every request it makes, and keeping its event log. */
    fun client(rung: String, token: String?): Actor {
        val log = mutableListOf<HeartRequest>()
        val events = mutableListOf<JsonObject>()
        val wire = object : Transport {
            override fun send(request: HeartRequest): HeartResponse {
                log += request
                val sent = token?.let { request.copy(headers = request.headers + ("Authorization" to it)) }
                return service.send(sent ?: request)
            }
        }
        val eventLog = EventLog(EventSink { events += Json.parseToJsonElement(it).jsonObject }, null, "conformance")
        val client = ArtieClient(CachingTransport(IntegrityCheck(wire)), Rung.valueOf(rung), events = eventLog)
        return Actor(client, log, events)
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
