package com.artie.heart.sims

import com.artie.heart.sims.build.BuildService
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import java.util.concurrent.CopyOnWriteArrayList

/** One request a service received: which service, its method, path and query, headers, and body. */
data class Logged(
    val service: String,
    val method: String,
    val url: String,
    val headers: Map<String, String?>,
    val body: String,
)

/**
 * The simulated services, by host, behind their faults, with every request
 * each one received kept in [log]. A request to any other host reaches the
 * control routes under `/_sim/`, which steps use to reset, set up and look.
 * Over a port, a client names the service it means in `X-Sim-Host`, so its
 * addresses stay the same as in memory.
 */
class Simulator {
    val build = BuildService()

    private val faults = mapOf(BUILD to Faults())
    private val services: Map<String, HttpHandler> = mapOf(BUILD to faults.getValue(BUILD).filter.then(build.handler))
    private val mapper = ObjectMapper()

    val log = CopyOnWriteArrayList<Logged>()

    init {
        reset()
    }

    /** Every service back to its seed, every fault cleared, the log emptied. */
    fun reset() {
        runBlocking { build.reset() }
        faults.values.forEach(Faults::clear)
        log.clear()
    }

    /** The build service's job [name], with [parameters] as the schema of its build act. */
    fun configureJob(name: String, parameters: String) = runBlocking { build.configure(name, parameters) }

    /** The faults of the service at [host]. */
    fun faults(host: String): Faults = faults[host] ?: throw IllegalArgumentException("no service $host")

    val handler: HttpHandler = { request ->
        val host = request.header(HOST_HEADER) ?: request.uri.host
        try {
            services[host]?.let { service ->
                log += Logged(host, request.method.name, pathOf(request), headersOf(request), request.bodyString())
                service(request)
            } ?: control(request)
        } catch (broken: RuntimeException) {
            // A simulator that breaks says how, to the one running it: a test double has nothing to hide.
            broken.printStackTrace()
            Response(Status.INTERNAL_SERVER_ERROR).header("Content-Type", "text/plain").body("$broken")
        }
    }

    private val control: HttpHandler = routes(
        "/_sim/reset" bind POST to { reset().let { Response(Status.NO_CONTENT) } },
        "/_sim/build/jobs/{name}" bind PUT to { request ->
            configureJob(request.path("name").orEmpty(), request.bodyString())
            Response(Status.NO_CONTENT)
        },
        "/_sim/{host}/acts/{act}/answer" bind POST to { request ->
            val answer = mapper.readTree(request.bodyString())
            faults(request.path("host").orEmpty())
                .answerAct(request.path("act").orEmpty(), answer["status"].asInt(), answer["body"].asText())
            Response(Status.NO_CONTENT)
        },
        "/_sim/{host}/forms/{form}/schema" bind PUT to { request ->
            faults(request.path("host").orEmpty()).declareSchema(request.path("form").orEmpty(), request.bodyString())
            Response(Status.NO_CONTENT)
        },
        "/_sim/log" bind GET to {
            Response(Status.OK).header("Content-Type", "application/json").body(mapper.writeValueAsString(log))
        },
    )

    private fun pathOf(request: Request) = request.uri.path + (request.uri.query.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")

    private fun headersOf(request: Request) =
        request.headers.filter { it.first != HOST_HEADER }.associate { (name, value) -> name to value }

    companion object {
        const val BUILD = "build.test"
        const val HOST_HEADER = "X-Sim-Host"
    }
}
