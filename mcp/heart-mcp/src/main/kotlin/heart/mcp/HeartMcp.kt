package heart.mcp

import heart.AccessRefused
import heart.ActRefused
import heart.ArtieClient
import heart.AuthTransport
import heart.CachingTransport
import heart.CoapBinding
import heart.CredentialSource
import heart.CredentialsUnavailable
import heart.Forbidden
import heart.NotAuthenticated
import heart.Dialect
import heart.EventLog
import heart.EventSink
import heart.HeartRepresentation
import heart.HeartRiseClient
import heart.HttpTransport
import heart.IllegalTransition
import heart.IntegrityCheck
import heart.InvalidPayload
import heart.RuleRefused
import heart.Rung
import heart.SchemaCheck
import heart.StaleAct
import heart.Trace
import heart.TraceContextTransport
import heart.UserAgentTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * HEART through MCP. An MCP host's model is the Reason step (SPEC §6.8): it
 * chooses among the acts and links of the resource ARTIE holds, through six
 * verbs that never change ([Tools]). ARTIE is Sync and Act: it checks each
 * choice against what it holds, builds and sends every request, and keeps
 * every address to itself. [bookmarks] are the only entry points, by name.
 */
class HeartMcp(private val client: HeartRiseClient, private val bookmarks: Map<String, String>) {

    /** The resources before the current one, for heart_back. Only ARTIE sees these addresses. */
    private val before = ArrayDeque<String>()

    /** One host session drives one client; its calls take turns. */
    private val turn = Mutex()

    /** Adds the six verbs to [server]. */
    fun register(server: Server) = Tools.all(bookmarks.keys).forEach { verb ->
        server.addTool(name = verb.name, description = verb.description, inputSchema = verb.input) { request ->
            val arguments = request.arguments ?: Tools.NONE
            turn.withLock { withContext(Dispatchers.IO) { answer(verb.name, arguments) } }
        }
    }

    private fun answer(tool: String, arguments: JsonObject): CallToolResult = try {
        success(
            when (tool) {
                "heart_look" -> look(arguments.text("service"))
                "heart_act" -> act(arguments.required("act"), arguments["fields"] as? JsonObject ?: Tools.NONE)
                "heart_follow" -> follow(arguments.required("rel"), arguments.text("title"))
                "heart_describe" -> describe(arguments.required("act"))
                "heart_explain" -> explain(arguments.required("act"))
                else -> back()
            },
        )
    } catch (e: IllegalStateException) {
        CallToolResult(listOf(TextContent(e.message ?: "$tool failed")), true, null, null)
    } catch (e: AccessRefused) {
        // Its message names the origin and the scheme sent, never the credential.
        CallToolResult(listOf(TextContent(e.message ?: "$tool was refused")), true, null, null)
    } catch (e: CredentialsUnavailable) {
        CallToolResult(listOf(TextContent(e.message ?: "$tool has no credential")), true, null, null)
    }

    private fun look(service: String?): JsonObject {
        if (service == null) return Views.of(client.enter(held().url))
        val bookmark = bookmarks[service]
            ?: error("No service named '$service'. The services are: ${bookmarks.keys.joinToString(", ")}.")
        before.clear()
        client.discover(bookmark)
        return Views.of(client.enter(bookmark))
    }

    /** Checks the choice against the held resource first; nothing is sent for a choice it does not offer. */
    private fun act(name: String, fields: JsonObject): JsonObject {
        val held = held()
        val form = held.affordance(name)
        val violations = form?.let { SchemaCheck.violations(fields, it.schema) }.orEmpty()
        return when {
            form == null ->
                rejected(held, "$name is not offered now. Offered: ${held.affordances.keys.joinToString(", ")}.")
            violations.isNotEmpty() -> rejected(held, "The fields do not satisfy $name's schema.", violations)
            else -> {
                // A GET form moves to another resource; any other act changes this one.
                if (form.method == "GET") before.addLast(held.url)
                send(held, name, fields)
            }
        }
    }

    private fun send(held: HeartRepresentation, name: String, fields: JsonObject): JsonObject = try {
        Views.of(client.act(name, fields), outcome("acted", "$name was done."))
    } catch (refusal: ActRefused) {
        Views.of(client.current ?: held, refused(refusal))
    } catch (refusal: AccessRefused) {
        val kind = when (refusal) {
            is NotAuthenticated -> "not-authenticated"
            is Forbidden -> "forbidden"
        }
        Views.of(held, outcome(kind, refusal.message.orEmpty()))
    }

    private fun follow(rel: String, title: String?): JsonObject {
        val held = held()
        if ((rel to title) !in Views.links(held)) return rejected(held, "There is no $rel link titled '$title' here.")
        before.addLast(held.url)
        return Views.of(client.follow(rel, title))
    }

    private fun describe(name: String): JsonObject {
        val held = held()
        if (held.affordance(name) == null) return rejected(held, "$name is not offered now.")
        val text = client.describe(name) ?: "The service gives no further description of $name."
        return Views.of(held, mapOf("description" to JsonPrimitive(text)))
    }

    private fun explain(name: String): JsonObject {
        val held = held()
        val reason = client.explain(name) ?: "The service gives no reason why $name is not offered."
        return Views.of(held, mapOf("explanation" to JsonPrimitive(reason)))
    }

    private fun back(): JsonObject {
        val previous = before.removeLastOrNull() ?: error("There is no resource before this one.")
        return Views.of(client.enter(previous))
    }

    private fun held(): HeartRepresentation =
        client.current ?: error("Look at a service first. The services are: ${bookmarks.keys.joinToString(", ")}.")

    companion object {
        /**
         * ARTIE composed as production composes it: named, carrying its trace
         * context above its own cache, which checks integrity on the wire;
         * asking for L2, or less if the service's bookmark says so. Its
         * [credentials] go only on the wire, to each one's own origin.
         */
        fun client(agent: String, events: EventSink, credentials: CredentialSource): HeartRiseClient {
            val name = "${UserAgentTransport.DEFAULT} ($agent)"
            val trace = Trace()
            // The credential is added just above the wire, below the cache: nothing the host's model sees holds it.
            val wire = CachingTransport(IntegrityCheck(AuthTransport(credentials, HttpTransport())))
            val transport = UserAgentTransport(name, TraceContextTransport(trace, wire))
            return ArtieClient(
                transport,
                Rung.L2,
                Dialect.HAL_SCHEMA_FORMS,
                events = EventLog(events, trace, name),
                bindings = mapOf("coap" to CoapBinding(trace = trace)),
            )
        }

        /** What heart-mcp declares to a host: tools, whose list never changes. */
        fun options() = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(false)), true)

        private fun success(content: JsonObject) =
            CallToolResult(listOf(TextContent(content.toString())), false, content, null)

        private fun rejected(held: HeartRepresentation, message: String, violations: List<String> = emptyList()) =
            Views.of(held, outcome("rejected-before-sending", "$message Nothing was sent.", violations))

        private fun refused(refusal: ActRefused): Map<String, JsonElement> = when (refusal) {
            is StaleAct -> outcome("stale", "${refusal.message}. The resource below is the fresh one.")
            is IllegalTransition -> outcome("not-offered", "${refusal.message}. The resource below is the fresh one.")
            is RuleRefused -> outcome("rule-refused", refusal.message.orEmpty(), refusal.violations)
            is InvalidPayload -> outcome("invalid-input", refusal.message.orEmpty(), refusal.violations)
            else -> outcome("failed", refusal.message.orEmpty())
        }

        private fun outcome(kind: String, message: String, violations: List<String> = emptyList()) = mapOf(
            "outcome" to buildJsonObject {
                put("kind", kind)
                put("message", message)
                if (violations.isNotEmpty()) {
                    putJsonArray("violations") { violations.forEach { add(JsonPrimitive(it)) } }
                }
            },
        )

        private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.required(name: String): String =
            text(name) ?: error("'$name' is required.")
    }
}
