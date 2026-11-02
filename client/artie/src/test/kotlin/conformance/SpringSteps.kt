package conformance

import heart.Dialect
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for spring.feature: the Spring service, Spring HATEOAS's affordances example as it answers. */
class SpringSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("a client asking in {} for the Spring service")
    fun client(dialect: String) {
        world.simulated()
        world.me = world.client("L0", null, DIALECTS.getValue(dialect))
    }

    @When("it enters the employees of the Spring service")
    fun enter() {
        world.rep = actor.client.enter(EMPLOYEES)
    }

    @When("it tries to enter the employees of the Spring service")
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

    @Then("the Spring service holds these employees")
    fun holds(table: DataTable) =
        assertEquals(table.asMaps(), world.sims.spring.all().map { mapOf("firstName" to it.firstName) })

    private fun text(value: kotlinx.serialization.json.JsonElement?) = value?.jsonPrimitive?.content.orEmpty()

    private companion object {
        const val EMPLOYEES = "http://spring.test/employees"
        val DIALECTS = mapOf("HAL-FORMS" to Dialect.HAL_FORMS, "HAL Schema Forms" to Dialect.HAL_SCHEMA_FORMS)
    }
}
