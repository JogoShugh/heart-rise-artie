package com.artie.heart.sims.employees

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.sims.Tokens
import com.fasterxml.jackson.databind.JsonNode
import org.http4k.core.HttpHandler

/**
 * A way the employees service changes after its clients were written, as services do between releases. Each is
 * a change to the declaration alone; heart-core renders it everywhere: the forms, the profile, the OpenAPI twin.
 */
enum class Change {
    /** Every act is served at a new address: hire at take-on, update at revise, dismiss at let-go. */
    MOVED,

    /** The input field role is called position. The state still says role. */
    RENAMED,

    /** Updating needs a reason. */
    REQUIRED,

    /** Updating is a PATCH, a JSON Merge Patch of the fields that change. */
    METHOD,

    /** Dismissing is offered only once notice is given, by a new act. */
    NOTICE,
}

/**
 * Spring's employees as a HEART service, declared once for heart-core: the
 * same employees at the same addresses, in both dialects, with a profile,
 * ETags and the reason an act is absent. A new employee comes from hiring,
 * which the bookmark offers; a dismissed one stays, with nothing to offer.
 * Declared with [changes], it is the service as it became after them.
 */
class HeartEmployees(private val staff: Staff, private val changes: Set<Change> = emptySet()) {
    private val roleField = if (Change.RENAMED in changes) "position" else "role"

    private val type = ResourceType(
        collection = "employees",
        profile = profile(changes),
        acts = listOfNotNull(
            Act("update", "update", updateSchema(), path = path("update", "revise"), method = updateMethod()),
            Act("dismiss", "dismiss", NO_INPUT, path = path("dismiss", "let-go"), method = Act.DELETE),
            Act("giveNotice", "give-notice", NO_INPUT).takeIf { Change.NOTICE in changes },
        ),
        bookmarkActs = listOf(Act("hire", "hire", employeeSchema(), path = path("hire", "take-on"))),
        offers = Offers(
            keys = listOf(Employee.WORKING, Employee.ON_NOTICE, Employee.DISMISSED),
            offered = ::offered,
            whyAbsent = { status, _ ->
                if (status == Employee.DISMISSED) "The employee was dismissed." else "Notice must be given first."
            },
        ),
        state = StateView<Employee, String>(
            keyOf = { it.status },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = ::body,
            keyField = "status",
        ),
        finding = Finding(
            noun = "employee",
            search = Named("searchEmployees", "search-employees"),
            lookup = Named("getEmployee", "get-employee"),
            nameDescription = "Part of the employee's first or last name; case is ignored",
            idTitle = "Employee number",
            idDescription = "The employee's number",
        ),
    )

    private val employees = HeartResource(
        layers = Layers(type),
        load = { id -> staff.get(id) ?: throw ResourceNotFoundException("no employee $id") },
        perform = { current, act, input ->
            val next = when (act) {
                "dismiss" -> current.copy(status = Employee.DISMISSED)
                "giveNotice" -> current.copy(status = Employee.ON_NOTICE)
                else -> updated(current, input.json)
            }
            Acted(staff.replace(current, next.copy(version = current.version + 1)))
        },
        find = { name, id ->
            staff.everyone()
                .filter { id == null || it.id == id }
                .filter { name == null || "${it.firstName} ${it.lastName}".contains(name, ignoreCase = true) }
                .map { Found(it.id, "${it.firstName} ${it.lastName}") }
        },
        performAtBookmark = { _, input -> Acted(hire(input.json)) },
    )

    val handler: HttpHandler = HeartHttp(
        listOf(employees),
        Credentials.parse(users = "", tokens = Tokens.sha256(TOKEN)),
        HeartSettings(title = "Employees", realm = PROFILE_NAME),
    )

    /** The acts each status offers, in the order of their relations, so every rung lists them alike. */
    private fun offered(status: String): List<String> = when (status) {
        Employee.WORKING -> if (Change.NOTICE in changes) listOf("giveNotice", "update") else listOf("dismiss", "update")
        Employee.ON_NOTICE -> listOf("dismiss", "update")
        else -> emptyList()
    }

    private fun path(name: String, moved: String) = if (Change.MOVED in changes) moved else name

    private fun updateMethod() = if (Change.METHOD in changes) Act.PATCH else Act.PUT

    /** An update's fields: all of them for a PUT, those that change for a PATCH; with a reason when one is needed. */
    private fun updated(current: Employee, body: JsonNode): Employee {
        fun text(name: String, otherwise: String) = body.get(name)?.asText() ?: otherwise
        return current.copy(
            firstName = text("firstName", current.firstName),
            lastName = text("lastName", current.lastName),
            role = text(roleField, current.role),
        )
    }

    private fun hire(body: JsonNode): Employee {
        fun text(name: String) = body.path(name).asText()
        return staff.hire(text("firstName"), text("lastName"), text(roleField))
    }

    private fun employeeSchema(): String = schema(required = listOf("firstName", "lastName", roleField))

    private fun updateSchema(): String {
        val reason = Change.REQUIRED in changes
        val required = if (Change.METHOD in changes) emptyList() else listOf("firstName", "lastName", roleField)
        return schema(required + listOfNotNull("reason".takeIf { reason }), reason)
    }

    private fun schema(required: List<String>, reason: Boolean = false): String {
        val fields = listOf("firstName", "lastName", roleField) + listOfNotNull("reason".takeIf { reason })
        val properties = fields.joinToString(", ") { """"$it": {"type": "string", "minLength": 1}""" }
        val names = required.joinToString(", ") { "\"$it\"" }
        return """{"type": "object", "required": [$names], "properties": {$properties}}"""
    }

    private fun body(employee: Employee): Map<String, Any?> = linkedMapOf(
        "id" to employee.id,
        "firstName" to employee.firstName,
        "lastName" to employee.lastName,
        "role" to employee.role,
        "status" to employee.status,
    )

    companion object {
        /** The Bearer token the employees service admits. */
        const val TOKEN = "employees-token"

        private const val PROFILE_NAME = "staff"

        private const val NO_INPUT = """{"type": "object", "properties": {}}"""

        fun profile(changes: Set<Change> = emptySet()) = Profile(
            name = PROFILE_NAME,
            title = "Employees",
            doc = "Spring HATEOAS's example employees, as a HEART service: hiring, updating and dismissing.",
            relations = listOfNotNull(
                Relation(
                    "hire", AlpsType.UNSAFE, "Hire an employee",
                    "Hires someone, in a role.",
                    "Offered by the bookmark, always; the answer is the new employee.",
                ),
                Relation(
                    "update", if (Change.METHOD in changes) AlpsType.UNSAFE else AlpsType.IDEMPOTENT,
                    "Update the employee",
                    "Sets the employee's name and role.",
                    "Offered while the employee is working or on notice.",
                ),
                Relation(
                    "dismiss", AlpsType.IDEMPOTENT, "Dismiss the employee",
                    "Dismisses the employee, who stays on record.",
                    if (Change.NOTICE in changes) {
                        "Offered once the employee is on notice; nothing is offered after."
                    } else {
                        "Offered while the employee is working; nothing is offered after."
                    },
                ),
                Relation(
                    "give-notice", AlpsType.UNSAFE, "Give notice",
                    "Puts the employee on notice, before dismissing them.",
                    "Offered while the employee is working; the employee is on notice after.",
                ).takeIf { Change.NOTICE in changes },
                Relation(
                    "search-employees", AlpsType.SAFE, "Find employees by name",
                    "Lists the employees whose name has the text.",
                    "A safe search; each result links to the employee, titled with their name.",
                ),
                Relation(
                    "get-employee", AlpsType.SAFE, "Get an employee by number",
                    "Goes to one employee, given their number.",
                    "A safe lookup; the result is the employee, with what may be done now.",
                ),
            ),
        )
    }
}
