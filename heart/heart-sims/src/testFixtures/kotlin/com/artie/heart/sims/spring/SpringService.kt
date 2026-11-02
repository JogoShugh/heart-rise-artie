package com.artie.heart.sims.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

/**
 * The Spring service: Spring HATEOAS's own affordances example
 * (spring-projects/spring-hateoas-examples, module affordances) as it answers,
 * byte for byte in shape, copied from a run of it on 1 November 2026. It is no
 * HEART service, and does nothing a HEART service would: HAL-FORMS only, its
 * templates' methods in lower case, no profile, no ETag, absolute links, and every
 * act answered 204 with no body (a create and an update name the employee in
 * Location). Asked for application/hal+json, it answers its own JSON: a `links`
 * array, no forms. A reset seeds Frodo and Bilbo, as the example's loader does.
 *
 * Switched to HEART ([answerAsHeart]), the same employees are served by
 * heart-core instead ([HeartEmployees]): both dialects, a profile and ETags.
 * A reset switches it back to answering as Spring.
 */
class SpringService {
    private val mapper = ObjectMapper()
    private val staff = Staff()
    private val heart = HeartEmployees(staff)

    @Volatile
    private var asHeart = false

    fun reset() {
        staff.reset()
        asHeart = false
    }

    /** From now on, until a reset, the employees are served as a HEART service. */
    fun answerAsHeart() {
        asHeart = true
    }

    /** Every employee still working, in id order. */
    fun all(): List<Employee> = staff.working()

    private val spring: HttpHandler = routes(
        "/employees" bind GET to { request -> list(request) },
        "/employees" bind POST to { request ->
            val made = fromBody(request, null)
            Response(Status.NO_CONTENT).header("Location", "${origin(request)}/employees/${made.id}")
        },
        "/employees/{id}" bind GET to { request ->
            staff.get(idOf(request))?.let { one(request, it) } ?: Response(Status.NOT_FOUND)
        },
        "/employees/{id}" bind PUT to { request ->
            val saved = fromBody(request, idOf(request))
            Response(Status.NO_CONTENT).header("Location", "${origin(request)}/employees/${saved.id}")
        },
        "/employees/{id}" bind DELETE to { request ->
            staff.remove(idOf(request))
            Response(Status.NO_CONTENT)
        },
    )

    val handler: HttpHandler = { request -> if (asHeart) heart.handler(request) else spring(request) }

    /** Spring's save: a new employee when there is no [id], the employee [id] as given otherwise. */
    private fun fromBody(request: Request, id: Long?): Employee {
        val body = mapper.readTree(request.bodyString())
        fun text(name: String) = body.path(name).asText("")
        return if (id == null) {
            staff.hire(text("firstName"), text("lastName"), text("role"))
        } else {
            staff.save(id, text("firstName"), text("lastName"), text("role"))
        }
    }

    private fun idOf(request: Request) = request.path("id")?.toLongOrNull() ?: -1

    /** Where the request came: Spring writes every link absolute, against the address it was asked at. */
    private fun origin(request: Request) = "http://${request.header(HOST_HEADER) ?: request.uri.authority}"

    private fun halForms(request: Request) = request.header("Accept").orEmpty().let { HAL_FORMS in it || HAL !in it }

    private fun list(request: Request): Response {
        val origin = origin(request)
        val body = if (halForms(request)) {
            mapper.createObjectNode().apply {
                putObject("_embedded").putArray("employees").apply { staff.everyone().forEach { add(employee(it, origin)) } }
                putObject("_links").putObject("self").put("href", "$origin/employees")
                set<ObjectNode>("_templates", templates(mapOf("default" to "post")))
            }
        } else {
            mapper.createObjectNode().apply {
                putArray("links").addObject().put("rel", "self").put("href", "$origin/employees")
                putArray("content").apply { staff.everyone().forEach { add(plain(it, origin)) } }
            }
        }
        return answer(request, body)
    }

    private fun one(request: Request, employee: Employee): Response {
        val origin = origin(request)
        return answer(request, if (halForms(request)) employee(employee, origin) else plain(employee, origin))
    }

    private fun answer(request: Request, body: ObjectNode) = Response(Status.OK)
        .header("Content-Type", if (halForms(request)) HAL_FORMS else HAL)
        .body(mapper.writeValueAsString(body))

    private fun fields(employee: Employee): ObjectNode = mapper.createObjectNode()
        .put("id", employee.id).put("firstName", employee.firstName)
        .put("lastName", employee.lastName).put("role", employee.role)

    private fun employee(employee: Employee, origin: String): ObjectNode = fields(employee).apply {
        putObject("_links").apply {
            putObject("self").put("href", "$origin/employees/${employee.id}")
            putObject("employees").put("href", "$origin/employees")
        }
        set<ObjectNode>("_templates", templates(mapOf("default" to "put", "deleteEmployee" to "delete")))
    }

    private fun plain(employee: Employee, origin: String): ObjectNode = fields(employee).apply {
        putArray("links").apply {
            addObject().put("rel", "self").put("href", "$origin/employees/${employee.id}")
            addObject().put("rel", "employees").put("href", "$origin/employees")
        }
    }

    /** Spring's templates: a method in lower case, and every field of an employee, or none for a delete. */
    private fun templates(methods: Map<String, String>): ObjectNode = mapper.createObjectNode().apply {
        methods.forEach { (name, method) ->
            putObject(name).apply {
                put("method", method)
                val properties = putArray("properties")
                if (method != "delete") FIELDS.forEach { properties.addObject().put("name", it) }
            }
        }
    }

    companion object {
        const val HOST_HEADER = "X-Sim-Host"
        private const val HAL_FORMS = "application/prs.hal-forms+json"
        private const val HAL = "application/hal+json"
        private val FIELDS = listOf("firstName", "id", "lastName", "role")
    }
}
