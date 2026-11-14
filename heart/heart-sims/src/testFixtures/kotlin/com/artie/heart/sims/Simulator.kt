package com.artie.heart.sims

import com.artie.heart.sims.build.BuildService
import com.artie.heart.sims.deploy.DeployService
import com.artie.heart.sims.deploy.Region
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.employees.Change
import com.artie.heart.sims.employees.EmployeesService
import com.artie.heart.sims.library.CallerService
import com.artie.heart.sims.library.LibraryService
import com.artie.heart.sims.library.RiseParties
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
 * addresses stay the same as in memory. Run with [alone], the simulator serves
 * that one service at its root to a client that names none, as a real service
 * would; the control routes stay under `/_sim/`.
 */
class Simulator(private val alone: String? = null) {
    val build = BuildService()
    val deploy = DeployService()
    val github = GitHubService()
    val employees = EmployeesService()
    val library = LibraryService(keysAt = { href -> RiseParties.keysAt(caller.handler, href) })

    /** Ana's replies, beside the library: each party finds the other's keys through links, as over HTTP. */
    val caller: CallerService = CallerService(watchKeys = { watch -> RiseParties.watchKeys(library.handler, watch) })

    private val faults = listOf(BUILD, DEPLOY, GITHUB, EMPLOYEES, LIBRARY, REPLIES, ELSEWHERE).associateWith { Faults() }
    private val services: Map<String, HttpHandler> = mapOf(
        BUILD to faults.getValue(BUILD).filter.then(build.handler),
        DEPLOY to faults.getValue(DEPLOY).filter.then(deploy.handler),
        GITHUB to faults.getValue(GITHUB).filter.then(github.handler),
        EMPLOYEES to faults.getValue(EMPLOYEES).filter.then(employees.handler),
        LIBRARY to faults.getValue(LIBRARY).filter.then(library.handler),
        REPLIES to faults.getValue(REPLIES).filter.then(caller.handler),
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
            employees.reset()
            library.reset()
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

    val handler: HttpHandler = handlerFor(alone)

    /** The simulator, serving [alone] at its root to a client that names no service; the control routes beside it. */
    fun handlerFor(alone: String?): HttpHandler = { request ->
        val host = request.header(HOST_HEADER)
            ?: request.uri.host.takeIf { it in services }
            ?: alone.takeUnless { request.uri.path.startsWith("/_sim/") }
            ?: ""
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
        "/_sim/employees/mode" bind PUT to { request ->
            employees.answerAs(EmployeesService.Mode.of(request.bodyString().trim()))
            Response(Status.NO_CONTENT)
        },
        "/_sim/employees/changes" bind PUT to { request ->
            val names = request.bodyString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            employees.change(names.map { Change.valueOf(it.uppercase()) }.toSet())
            Response(Status.NO_CONTENT)
        },
        "/_sim/github/dam" bind PUT to { request ->
            github.dam(com.artie.heart.gateway.Dam.parse(request.bodyString()))
            Response(Status.NO_CONTENT)
        },
        "/_sim/github/meddle" bind PUT to { request ->
            val meddle = mapper.readTree(request.bodyString())
            val change = meddle.properties().filter { it.key != "issue" }.associate { it.key to it.value.asText() }
            github.meddle(meddle.path("issue").asLong(), change)
            Response(Status.NO_CONTENT)
        },
        "/_sim/employees/meddle" bind PUT to { request ->
            val meddle = mapper.readTree(request.bodyString())
            employees.meddle(meddle.path("employee").asLong(), meddle.path("role").asText())
            Response(Status.NO_CONTENT)
        },
        "/_sim/employees/employees" bind GET to {
            Response(Status.OK).header("Content-Type", "application/json").body(mapper.writeValueAsString(employees.all()))
        },
        // RISE: the library lends The Hobbit with a hold waiting, and Ana opens her replies.
        "/_sim/rise" bind POST to {
            library.lend(listOf(RiseParties.HOBBIT))
            RiseParties.REPLIES.forEach(caller::open)
            Response(Status.NO_CONTENT)
        },
        "/_sim/library/holds/{loan}/release" bind POST to { request ->
            library.releaseHold(request.path("loan").orEmpty().toLong())
            Response(Status.NO_CONTENT)
        },
        // The library's own record that it has answered a watch, and Ana's that a watch answers her reply.
        "/_sim/library/watches/{id}/answered" bind POST to { request ->
            library.answered(request.path("id").orEmpty().toLong())
            Response(Status.NO_CONTENT)
        },
        "/_sim/replies/{reply}/asked" bind POST to { request ->
            caller.asked(request.path("reply").orEmpty(), request.bodyString())
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
        const val DEPLOY = "deploy.test"
        const val GITHUB = "github.test"

        /** Spring HATEOAS's example employees, as a HEART service, or as stock Spring serves them. */
        const val EMPLOYEES = "employees.test"

        /** Riverside Public Library, the library of "The Web That Wasn't": a member's loans, renewed and returned. */
        const val LIBRARY = "library.test"

        /** Ana's replies, where the library answers her requests (RISE). */
        const val REPLIES = "replies.test"

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
