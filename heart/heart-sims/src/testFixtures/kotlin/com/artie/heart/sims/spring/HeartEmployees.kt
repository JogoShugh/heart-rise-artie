package com.artie.heart.sims.spring

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
 * Spring's employees as a HEART service, declared once for heart-core: the
 * same employees at the same addresses, in both dialects, with a profile,
 * ETags and the reason an act is absent. A new employee comes from hiring,
 * which the bookmark offers; a dismissed one stays, with nothing to offer.
 */
class HeartEmployees(private val staff: Staff) {
    private val type = ResourceType(
        collection = "employees",
        profile = PROFILE,
        acts = listOf(
            Act("update", "update", EMPLOYEE_SCHEMA, method = Act.PUT),
            Act("dismiss", "dismiss", NO_INPUT, method = Act.DELETE),
        ),
        bookmarkActs = listOf(Act("hire", "hire", EMPLOYEE_SCHEMA)),
        offers = Offers(
            keys = listOf(Employee.WORKING, Employee.DISMISSED),
            offered = { status -> if (status == Employee.WORKING) listOf("update", "dismiss") else emptyList() },
            whyAbsent = { _, _ -> "The employee was dismissed." },
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
            val next = if (act == "dismiss") {
                current.copy(status = Employee.DISMISSED)
            } else {
                fun text(name: String) = input.json.path(name).asText()
                current.copy(firstName = text("firstName"), lastName = text("lastName"), role = text("role"))
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
        HeartSettings(title = "Employees", realm = PROFILE.name),
    )

    private fun hire(body: JsonNode): Employee {
        fun text(name: String) = body.path(name).asText()
        return staff.hire(text("firstName"), text("lastName"), text("role"))
    }

    private fun body(employee: Employee): Map<String, Any?> = linkedMapOf(
        "id" to employee.id,
        "firstName" to employee.firstName,
        "lastName" to employee.lastName,
        "role" to employee.role,
        "status" to employee.status,
    )

    companion object {
        /** The Bearer token the Spring service admits when it answers as HEART. */
        const val TOKEN = "spring-token"

        private const val EMPLOYEE_SCHEMA = """{"type": "object", "required": ["firstName", "lastName", "role"],
            "properties": {"firstName": {"type": "string", "minLength": 1},
            "lastName": {"type": "string", "minLength": 1}, "role": {"type": "string", "minLength": 1}}}"""

        private const val NO_INPUT = """{"type": "object", "properties": {}}"""

        val PROFILE = Profile(
            name = "staff",
            title = "Employees",
            doc = "Spring HATEOAS's example employees, as a HEART service: hiring, updating and dismissing.",
            relations = listOf(
                Relation(
                    "hire", AlpsType.UNSAFE, "Hire an employee",
                    "Hires someone, in a role.",
                    "Offered by the bookmark, always; the answer is the new employee.",
                ),
                Relation(
                    "update", AlpsType.IDEMPOTENT, "Update the employee",
                    "Sets the employee's name and role.",
                    "Offered while the employee is working.",
                ),
                Relation(
                    "dismiss", AlpsType.IDEMPOTENT, "Dismiss the employee",
                    "Dismisses the employee, who stays on record.",
                    "Offered while the employee is working; nothing is offered after.",
                ),
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
