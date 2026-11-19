package com.artie.heart.http4k.browser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Headless Chrome, driven over the DevTools protocol with the JDK's own WebSocket: one browser for the run, a new tab
 * for each scenario. Chrome is found at CHROME, or where macOS and Linux install it; without one, [available] is false.
 */
object Chrome {
    private val mapper = ObjectMapper()
    private val http = HttpClient.newHttpClient()

    private val binary: String? by lazy {
        listOfNotNull(
            System.getenv("CHROME"),
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium",
        ).firstOrNull { Files.isExecutable(Path.of(it)) }
    }

    val available: Boolean get() = binary != null

    private val port: Int by lazy { start() }

    private fun start(): Int {
        val profile = Files.createTempDirectory("heart-chrome")
        val process = ProcessBuilder(
            checkNotNull(binary),
            "--headless=new",
            "--use-mock-keychain",
            "--no-first-run",
            "--disable-gpu",
            // Each scenario's tab is opened behind the others: Chrome must not throttle or freeze it for that.
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding",
            "--remote-debugging-port=0",
            "--user-data-dir=$profile",
            "about:blank",
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })
        val active = profile.resolve("DevToolsActivePort")
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(STARTUP_SECONDS)
        while (!Files.exists(active) || Files.readAllLines(active).isEmpty()) {
            check(System.nanoTime() < until) { "Chrome did not start" }
            Thread.sleep(POLL_MS)
        }
        return Files.readAllLines(active).first().trim().toInt()
    }

    /** A new tab, its own DevTools session. */
    fun tab(): Tab {
        val created = http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/new?about:blank"))
                .PUT(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val target = mapper.readTree(created.body())
        // Brought to the front, so a busy machine's Chrome does not leave it waiting behind the tab before it.
        http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/json/activate/${target["id"].asText()}")).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        return Tab(target["id"].asText(), target["webSocketDebuggerUrl"].asText())
    }

    /** One tab: commands sent and answered in order, by id. */
    class Tab(private val id: String, socketUrl: String) : AutoCloseable {
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonNode>>()
        private val socket: WebSocket = http.newWebSocketBuilder()
            .buildAsync(URI(socketUrl), Listener())
            .get(STARTUP_SECONDS, TimeUnit.SECONDS)

        /** Sends [method] with [params]; its answer's result. */
        fun send(method: String, params: Map<String, Any?> = emptyMap()): JsonNode {
            val n = ids.incrementAndGet()
            val answer = CompletableFuture<JsonNode>()
            pending[n] = answer
            socket.sendText(mapper.writeValueAsString(mapOf("id" to n, "method" to method, "params" to params)), true)
            val message = answer.get(ANSWER_SECONDS, TimeUnit.SECONDS)
            message["error"]?.let { error("$method: $it") }
            return message["result"]
        }

        /** [expression]'s value in the page, a promise awaited. */
        fun eval(expression: String): JsonNode {
            val result = send(
                "Runtime.evaluate",
                mapOf("expression" to expression, "returnByValue" to true, "awaitPromise" to true),
            )
            result["exceptionDetails"]?.let { error("$expression: ${it["exception"]?.get("description") ?: it}") }
            return result["result"]["value"] ?: mapper.nullNode()
        }

        /** Waits until [expression] is true in the page; [what] names it when it never is. */
        fun waitFor(what: String, expression: String) {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(ANSWER_SECONDS)
            while (!eval("Boolean($expression)").asBoolean()) {
                check(System.nanoTime() < until) { "never: $what" }
                Thread.sleep(POLL_MS)
            }
        }

        private val paused = LinkedBlockingQueue<JsonNode>()

        /**
         * Runs [act], which starts a navigation, and answers the new document's request with 204 No Content, so the
         * page stays where it is, its scripts' coverage with it; the address the navigation asked for.
         */
        fun heldNavigation(act: () -> Unit): String {
            send("Fetch.enable", mapOf("patterns" to listOf(mapOf("urlPattern" to "*", "resourceType" to "Document"))))
            act()
            val request = checkNotNull(paused.poll(ANSWER_SECONDS, TimeUnit.SECONDS)) { "no navigation" }
            send(
                "Fetch.fulfillRequest",
                mapOf("requestId" to request["requestId"].asText(), "responseCode" to NO_CONTENT),
            )
            send("Fetch.disable")
            return request["request"]["url"].asText()
        }

        override fun close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").get(ANSWER_SECONDS, TimeUnit.SECONDS)
            http.send(
                HttpRequest.newBuilder(
                    URI("http://127.0.0.1:$port/json/close/$id")
                ).timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        }

        private inner class Listener : WebSocket.Listener {
            private val text = StringBuilder()

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                text.append(data)
                if (last) {
                    val message = mapper.readTree(text.toString()) as ObjectNode
                    text.setLength(0)
                    message["id"]?.let { pending.remove(it.asInt())?.complete(message) }
                    if (message["method"]?.asText() == "Fetch.requestPaused") paused.add(message["params"])
                }
                webSocket.request(1)
                return null
            }
        }
    }

    private const val STARTUP_SECONDS = 20L
    private const val ANSWER_SECONDS = 15L
    private const val POLL_MS = 50L
    private const val NO_CONTENT = 204
}
