package com.artie.heart.sims

import com.artie.heart.sims.build.BuildService
import com.artie.heart.sims.deploy.DeployService
import com.artie.heart.sims.deploy.Region
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.spring.SpringService
import com.artie.heart.Rung
import com.fasterxml.jackson.core.type.TypeReference
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

/**
 * One exchange a service had: which service, the request's method, path and
 * query, headers and body, and the answer's status and ETag.
 */
data class Logged(
    val service: String,
    val method: String,
    val url: String,
    val headers: Map<String, String?>,
    val body: String,
    val status: Int,
    val etag: String?,
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
    val deploy = DeployService()
    val github = GitHubService()
    val spring = SpringService()

    private val faults = mapOf(BUILD to Faults(), DEPLOY to Faults(), GITHUB to Faults(), SPRING to Faults(), ELSEWHERE to Faults())
    private val services: Map<String, HttpHandler> = mapOf(
        BUILD to faults.getValue(BUILD).filter.then(build.handler),
        DEPLOY to faults.getValue(DEPLOY).filter.then(deploy.handler),
        GITHUB to faults.getValue(GITHUB).filter.then(github.handler),
        SPRING to faults.getValue(SPRING).filter.then(spring.handler),
        ELSEWHERE to faults.getValue(ELSEWHERE).filter.then(elsewhere),
    )
    private val mapper = ObjectMapper().findAndRegisterModules()

    val log = CopyOnWriteArrayList<Logged>()

    init {
        reset()
    }

    /** Every service back to its seed, every fault cleared, the log emptied. */
    fun reset() {
        runBlocking {
            build.reset()
            deploy.reset()
            github.reset()
            spring.reset()
        }
        faults.values.forEach(Faults::clear)
        log.clear()
    }

    /** The build service's job [name], with [parameters] as the schema of its build act. */
    fun configureJob(name: String, parameters: String) = runBlocking { build.configure(name, parameters) }

    /** The deploy service's app [app] offered [regions], by environment. */
    fun setRegions(app: String, regions: Map<String, List<Region>>) = runBlocking { deploy.setRegions(app, regions) }

    /** A release of the deploy service's app [app], built with [status]. */
    fun publishRelease(app: String, version: String, status: String) =
        runBlocking { deploy.publish(app, version, status) }

    /** Someone else starts deploying the deploy service's app [app]. */
    fun startDeploying(app: String) = runBlocking { deploy.startDeploying(app) }

    /** The deploy service serves no higher than [rung]. */
    fun deployServesUpTo(rung: Rung) {
        deploy.limits.maxRung = rung
    }

    /** The faults of the service at [host]. */
    fun faults(host: String): Faults = faults[host] ?: throw IllegalArgumentException("no service $host")

    val handler: HttpHandler = { request ->
        val host = request.header(HOST_HEADER) ?: request.uri.host
        try {
            services[host]?.let { service ->
                service(request).also { answer ->
                    val asked = Logged(
                        host, request.method.name, pathOf(request), headersOf(request), request.bodyString(),
                        answer.status.code, answer.header("ETag"),
                    )
                    log += asked
                }
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
        "/_sim/deploy/apps/{name}/regions" bind PUT to { request ->
            val regions = mapper.readValue(request.bodyString(), REGIONS)
            setRegions(request.path("name").orEmpty(), regions)
            Response(Status.NO_CONTENT)
        },
        "/_sim/deploy/apps/{name}/releases" bind POST to { request ->
            val release = mapper.readTree(request.bodyString())
            publishRelease(request.path("name").orEmpty(), release["version"].asText(), release["status"].asText())
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
        "/_sim/{host}/templates/{name}" bind PUT to { request ->
            faults(request.path("host").orEmpty()).template(request.path("name").orEmpty(), request.bodyString())
            Response(Status.NO_CONTENT)
        },
        "/_sim/{host}/plain" bind POST to { request ->
            val plain = mapper.readTree(request.bodyString())
            faults(request.path("host").orEmpty()).serve(
                plain["path"].asText(), plain["status"].asInt(), plain["body"].asText(), plain["validator"]?.asText(),
            )
            Response(Status.NO_CONTENT)
        },
        "/_sim/{host}/faults" bind POST to { request ->
            FaultSettings.apply(faults(request.path("host").orEmpty()), mapper.readTree(request.bodyString()))
            Response(Status.NO_CONTENT)
        },
        "/_sim/deploy/rung" bind PUT to { request ->
            deployServesUpTo(Rung.valueOf(request.bodyString().trim()))
            Response(Status.NO_CONTENT)
        },
        "/_sim/deploy/apps/{name}/deployments" bind POST to { request ->
            startDeploying(request.path("name").orEmpty())
            Response(Status.NO_CONTENT)
        },
        "/_sim/spring/employees" bind GET to {
            Response(Status.OK).header("Content-Type", "application/json").body(mapper.writeValueAsString(spring.all()))
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
        const val DEPLOY = "deploy.test"
        const val GITHUB = "github.test"

        /** Spring HATEOAS's affordances example, as it answers: no HEART service. */
        const val SPRING = "spring.test"

        /** Another origin, for an act a form sends away from the service it came from. */
        const val ELSEWHERE = "elsewhere.test"

        /** Another origin answers any request as a HEART resource with nothing to offer. */
        private val elsewhere: HttpHandler = {
            Response(Status.OK).header("Content-Type", "application/hal+json")
                .header("Link", "<https://w3id.org/heart-rise/hal-schema-forms>; rel=\"profile\"")
                .body("""{"_links": {"self": {"href": "/comments"}}, "_forms": {}}""")
        }
        private val REGIONS = object : TypeReference<Map<String, List<Region>>>() {}
        const val HOST_HEADER = "X-Sim-Host"
    }
}
