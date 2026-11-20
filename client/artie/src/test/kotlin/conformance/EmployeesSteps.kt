package conformance

import com.artie.heart.sims.employees.Change
import com.artie.heart.sims.employees.EmployeesService
import heart.Dialect
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for employees.feature: a HEART service, or answering as stock Spring HATEOAS's affordances example. */
class EmployeesSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("a client asking in {} at rung {word} for the employees service")
    fun client(dialect: String, rung: String) {
        world.simulated()
        world.me = world.client(rung, "Bearer employees-token", DIALECTS.getValue(dialect))
    }

    @Given("the employees service has changed: {word}")
    fun changed(change: String) = world.sims.employees.change(setOf(Change.valueOf(change.uppercase())))

    @Given("someone else will change employee {int}'s role to {string} right after a client reads him")
    fun meddles(id: Int, role: String) = world.sims.employees.meddle(id.toLong(), role)

    @Given("the employees service answers as stock Spring HATEOAS")
    fun stockSpring() = world.sims.employees.answerAs(EmployeesService.Mode.STOCK_SPRING)

    @When("it enters the employees service at its bookmark")
    fun enterBookmark() {
        world.rep = actor.client.enter(BOOKMARK)
    }

    @When("it enters the employees of the employees service")
    fun enter() {
        world.rep = actor.client.enter(EMPLOYEES)
    }

    @When("it tries to enter the employees of the employees service")
    fun tryEnter() = world.trying { actor.client.enter(EMPLOYEES) }

    @When("it follows {string} with no title")
    fun follow(rel: String) {
        world.rep = actor.client.follow(rel, null)
    }

    @Then("it embeds these employees")
    fun embeds(table: DataTable) {
        val held = world.held.embeddedByRel.values.flatten().map { employee ->
            mapOf("firstName" to text(employee.state["firstName"]), "role" to text(employee.state["role"]))
        }
        assertEquals(table.asMaps(), held)
    }

    @Then("the employees service holds these employees")
    fun holds(table: DataTable) =
        assertEquals(table.asMaps(), world.sims.employees.all().map { mapOf("firstName" to it.firstName) })

    private fun text(value: kotlinx.serialization.json.JsonElement?) = value?.jsonPrimitive?.content.orEmpty()

    private companion object {
        const val BOOKMARK = "http://employees.test/"
        const val EMPLOYEES = "http://employees.test/employees"
        val DIALECTS = mapOf("HAL-FORMS" to Dialect.HAL_FORMS, "HAL Schema Forms" to Dialect.HAL_SCHEMA_FORMS)
    }
}
