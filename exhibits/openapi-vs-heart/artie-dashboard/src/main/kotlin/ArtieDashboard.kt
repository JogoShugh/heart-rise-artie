import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import heart.AccessRefused
import heart.ActRefused
import heart.ArtieAgent
import heart.ArtieClient
import heart.AuthTransport
import heart.CachingTransport
import heart.ClaudeReasoner
import heart.CredentialSources
import heart.Decision
import heart.Dialect
import heart.HeartRepresentation
import heart.HeartRequest
import heart.HeartResponse
import heart.HttpTransport
import heart.IntegrityCheck
import heart.Rung
import heart.Selection
import heart.Transport
import heart.UserAgentTransport
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The ARTIE dashboard: a web page for any HEART service, written once and never
 * changed for a server change. It knows the service's bookmark and nothing else:
 * no statuses, no rules, no act names. Every card is rendered from the HEART
 * response ARTIE just received (Render), and re-synced every 2 seconds, so a new
 * state or a new act on the server shows up here as soon as the server offers it.
 *
 * Routes: `/` (a page, at `?at=` or the bookmark), `/card` (a card, for htmx),
 * `/act` (acts on an offered form, by its key), `/ask` (ArtieAgent with Claude),
 * `/_calls` (every request ARTIE sent the service, with its status).
 */

/** Every request that reached the service, with its status, for the regression suite. */
class CallLog(private val next: Transport) : Transport {
    val calls = CopyOnWriteArrayList<Map<String, Any>>()
    override fun send(request: HeartRequest): HeartResponse =
        next.send(request).also { calls += mapOf("method" to request.method, "path" to URI(request.url).path, "status" to it.status) }
}

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 3002
    val bookmark = System.getenv("HEART_BOOKMARK") ?: "http://localhost:8080/"
    val credentials = CredentialSources.standard(System.getenv(), Path.of(System.getProperty("user.home")))
    val log = CallLog(HttpTransport())
    // One cache for the dashboard: shared layers are fetched once, for every card.
    val wire = CachingTransport(IntegrityCheck(AuthTransport(credentials, log)))
    val transport = UserAgentTransport("${UserAgentTransport.DEFAULT} (ARTIE dashboard)", wire)
    // A client per request, over the shared cache: each card is one sync of one resource.
    fun client() = ArtieClient(transport, Rung.L2, Dialect.HAL_SCHEMA_FORMS)
    val htmx = Thread.currentThread().contextClassLoader
        .getResourceAsStream("META-INF/resources/webjars/htmx.org/2.0.3/dist/htmx.min.js")!!.readAllBytes()

    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.executor = Executors.newFixedThreadPool(THREADS)
    server.createContext("/") { exchange ->
        try {
            val query = query(exchange)
            val at = query["at"] ?: bookmark
            when (exchange.requestURI.path) {
                "/htmx.min.js" -> send(exchange, "text/javascript", htmx)
                "/_calls" -> send(exchange, "application/json", json(log.calls))
                "/card" -> send(exchange, HTML, Render.card(client().enter(at)))
                "/act" -> send(exchange, HTML, act(client(), at, form(exchange)))
                "/ask" -> send(exchange, HTML, ask(client(), at, form(exchange)["intent"].orEmpty()))
                else -> send(exchange, HTML, Render.page(Render.card(client().enter(at))))
            }
        } catch (failure: Exception) {
            send(exchange, "text/plain", "The dashboard failed: ${failure.message}".toByteArray(), status = 500)
        }
    }
    server.start()
    println("ARTIE dashboard on http://localhost:$port/ for $bookmark")
}

/** Acts on the offered form named in `_form`, with the fields typed by its schema, and renders the answer. */
private fun act(client: ArtieClient, at: String, fields: Map<String, String>): String {
    val held = client.enter(at)
    val key = fields["_form"].orEmpty()
    val schema = held.affordance(key)?.schema ?: return Render.card(held, "$key is not offered now.")
    return try {
        Render.card(client.act(key, typed(fields - "_form", schema)))
    } catch (refusal: ActRefused) {
        Render.card(client.current ?: held, refusal.message.orEmpty())
    } catch (refusal: AccessRefused) {
        Render.card(held, refusal.message.orEmpty())
    }
}

/** ArtieAgent with Claude: the model picks one of the offered forms or links; ARTIE checks and acts. */
private fun ask(client: ArtieClient, at: String, intent: String): String {
    val key = System.getenv("ANTHROPIC_API_KEY") ?: Path.of(System.getProperty("user.home"), ".anthropic/api_key")
        .takeIf { Files.isRegularFile(it) }?.let { Files.readString(it).trim() }
        ?: return Render.card(client.enter(at), "No Anthropic API key is set.")
    val decision = ArtieAgent(client, ClaudeReasoner(HttpTransport(), key)).handle(intent, at)
    val said = when (decision) {
        is Decision.Acted -> "ARTIE did ${(decision.selection as Selection.Act).affordance}."
        else -> decision.toString()
    }
    return Render.card(client.current ?: client.enter(at), said)
}

private fun typed(fields: Map<String, String>, schema: JsonObject): JsonObject {
    val properties = (schema["properties"] as? JsonObject).orEmpty()
    return buildJsonObject {
        fields.filterValues { it.isNotEmpty() }.forEach { (name, value) ->
            val type = properties[name]?.jsonObject?.get("type")?.jsonPrimitive?.content
            put(name, when (type) {
                "integer" -> JsonPrimitive(value.toLong())
                "number" -> JsonPrimitive(value.toDouble())
                "boolean" -> JsonPrimitive(value.toBoolean())
                else -> JsonPrimitive(value)
            })
        }
    }
}

private fun query(exchange: HttpExchange): Map<String, String> = pairs(exchange.requestURI.rawQuery.orEmpty())

private fun form(exchange: HttpExchange): Map<String, String> =
    pairs(String(exchange.requestBody.readAllBytes(), Charsets.UTF_8))

private fun pairs(text: String): Map<String, String> =
    text.split('&').filter { '=' in it }.associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
    }

private fun json(calls: List<Map<String, Any>>): ByteArray =
    calls.joinToString(",", "[", "]") { call ->
        call.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":" + if (v is Number) "$v" else "\"$v\"" }
    }.toByteArray()

private fun send(exchange: HttpExchange, type: String, body: String) = send(exchange, type, body.toByteArray())

private fun send(exchange: HttpExchange, type: String, body: ByteArray, status: Int = 200) {
    exchange.responseHeaders.add("Content-Type", type)
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}

private const val HTML = "text/html; charset=utf-8"
private const val THREADS = 8
