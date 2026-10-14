package steps

import heart.Credential
import heart.CredentialSource
import heart.EventSink
import heart.Origin
import heart.mcp.HeartMcp
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An MCP host and heart-mcp in one test process, joined by two pipes. Each
 * end is the official SDK's own stdio transport, so every call crosses the
 * wire as JSON-RPC, as it does between Claude Code and the heart-mcp process.
 */
class McpHost {
    /** Where the garden is: a running server-spring (scripts/mcp-check.sh starts one). */
    val baseUrl: String = (System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080").trimEnd('/')

    /** ARTIE's event log for this scenario, one JSON line per decision. */
    val events = CopyOnWriteArrayList<String>()

    private lateinit var client: Client

    /** The last tool result the host received. */
    var last: CallToolResult? = null
        private set

    /** Every tool result the host received, in order. */
    val results = CopyOnWriteArrayList<CallToolResult>()

    /** heart-mcp's credentials: ARTIE's standard sources, unless a scenario replaces them. */
    var credentials: CredentialSource = Swappable(McpSteps.standardCredentials())

    /** The source a scenario can swap mid-session, as a credential revoked on the server would be. */
    class Swappable(@Volatile var inner: CredentialSource) : CredentialSource {
        override fun credentialFor(origin: Origin): Credential? = inner.credentialFor(origin)
        override fun renewed(origin: Origin): Credential? = inner.renewed(origin)
    }

    fun connect(service: String) = runBlocking {
        val toServer = PipedOutputStream()
        val serverIn = PipedInputStream(toServer, PIPE_BYTES)
        val toClient = PipedOutputStream()
        val clientIn = PipedInputStream(toClient, PIPE_BYTES)

        val artie = HeartMcp.client("heart-mcp test", EventSink { events += it }, credentials)
        val server = Server(Implementation("heart-mcp", "test"), HeartMcp.options())
        HeartMcp(artie, mapOf(service to "$baseUrl/")).register(server)
        server.createSession(StdioServerTransport(serverIn.asSource().buffered(), toClient.asSink().buffered()))

        client = Client(Implementation("test-host", "0"))
        client.connect(StdioClientTransport(clientIn.asSource().buffered(), toServer.asSink().buffered()))
    }

    fun tools(): List<Tool> = runBlocking { client.listTools(ListToolsRequest()).tools }

    fun call(tool: String, arguments: JsonObject): CallToolResult = runBlocking {
        client.callTool(CallToolRequest(CallToolRequestParams(name = tool, arguments = arguments)))
    }.also {
        last = it
        results += it
    }

    private companion object {
        const val PIPE_BYTES = 1 shl 20
    }
}
