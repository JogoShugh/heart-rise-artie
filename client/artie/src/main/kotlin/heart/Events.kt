package heart

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Instant

/**
 * Where ARTIE's event lines go. A sink never fails the client: a line that
 * cannot be written is dropped, because the log reports on behaviour and
 * must not change it.
 */
fun interface EventSink {
    fun write(line: String)
}

object EventSinks {
    /** The default: no log, no cost. */
    val NONE = EventSink { }

    /** Appends each line to a JSON Lines file. */
    fun file(path: Path) = EventSink { line ->
        runCatching {
            synchronized(this) { Files.writeString(path, line + "\n", CREATE, APPEND) }
        }
    }

    /** POSTs each line, as one JSON object, to [url] through [transport]. */
    fun post(url: String, transport: Transport) = EventSink { line ->
        runCatching { transport.send(HeartRequest("POST", url, mapOf("Content-Type" to "application/json"), line)) }
    }

    /**
     * The log as the environment asks for it: `ARTIE_EVENTS` names a JSON
     * Lines file, `ARTIE_EVENTS_URL` a URL to POST to. Either, both or
     * neither; neither means no log.
     */
    fun fromEnvironment(env: Map<String, String>, transport: Transport): EventSink {
        val sinks = listOfNotNull(
            env["ARTIE_EVENTS"]?.takeIf { it.isNotBlank() }?.let { file(Path.of(it)) },
            env["ARTIE_EVENTS_URL"]?.takeIf { it.isNotBlank() }?.let { post(it, transport) },
        )
        return when (sinks.size) {
            0 -> NONE
            1 -> sinks.single()
            else -> EventSink { line -> sinks.forEach { it.write(line) } }
        }
    }
}

/**
 * ARTIE's event log (hack log, 2026-10-10): one line per decision, so a
 * watcher can see choices that send nothing. Each line uses OpenTelemetry
 * log data model fields (`timestamp`, `trace_id`, `span_id`,
 * `severity_text`, `event_name`, `body`, `attributes`, `resource`) and
 * OpenTelemetry semantic convention names where one exists (`url.full`,
 * `http.request.method`, `http.response.status_code`). `span_id` is the
 * span of the request the decision was about, so the line ties to that
 * exact request on the wire.
 */
class EventLog(private val sink: EventSink, private val trace: Trace?, private val agent: String) {

    fun emit(event: String, body: String, url: String?, attributes: Map<String, String?> = emptyMap()) {
        if (sink === EventSinks.NONE) return
        val line = buildJsonObject {
            put("timestamp", Instant.now().toString())
            put("trace_id", trace?.traceId.orEmpty())
            put("span_id", url?.let { trace?.spanFor(it) }.orEmpty())
            put("severity_text", "INFO")
            put("event_name", event)
            put("body", body)
            putJsonObject("attributes") {
                url?.let { put("url.full", it) }
                attributes.forEach { (name, value) -> value?.let { put(name, it) } }
            }
            putJsonObject("resource") {
                put("service.name", "ARTIE")
                put("service.version", VERSION)
                put("user_agent.original", agent)
            }
        }
        sink.write(line.toString())
    }

    companion object {
        private const val VERSION = "0.5"
        val OFF = EventLog(EventSinks.NONE, null, UserAgentTransport.DEFAULT)
    }
}
