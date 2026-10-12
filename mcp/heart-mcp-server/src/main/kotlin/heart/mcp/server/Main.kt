package heart.mcp.server

import heart.EventSinks
import heart.HttpTransport
import heart.mcp.HeartMcp
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.system.exitProcess

/**
 * heart-mcp over stdio, as an MCP host starts it. The services come from
 * HEART_BOOKMARKS ("garden=http://localhost:8080/,tickets=http://localhost:8282/");
 * ARTIE's event log from ARTIE_EVENTS (a file) or ARTIE_EVENTS_URL. Stdout
 * carries the protocol, so every message for a person goes to stderr.
 */
fun main(): Unit = runBlocking {
    val bookmarks = System.getenv("HEART_BOOKMARKS").orEmpty()
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        .associate { entry -> entry.substringBefore('=').trim() to entry.substringAfter('=').trim() }
        .filter { (name, url) -> name.isNotEmpty() && url.isNotEmpty() }
    if (bookmarks.isEmpty()) {
        System.err.println("heart-mcp: set HEART_BOOKMARKS, e.g. garden=http://localhost:8080/")
        exitProcess(2)
    }
    val events = EventSinks.fromEnvironment(System.getenv(), HttpTransport())
    val server = Server(Implementation("heart-mcp", "0.1.0"), HeartMcp.options())
    HeartMcp(HeartMcp.client("heart-mcp", events), bookmarks).register(server)
    val stdio = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered())
    val session = server.createSession(stdio)
    val closed = Job()
    session.onClose { closed.complete() }
    closed.join()
}
