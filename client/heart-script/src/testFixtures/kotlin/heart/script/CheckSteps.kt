package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for checks as facts (checks.feature): a resource's whole state, and the facts a trace kept. */
class CheckSteps(private val steps: ScriptSteps) {

    @Given("the resource at {string} holds")
    fun holds(address: String, body: String) {
        steps.service.bodies[address] = Json.parseToJsonElement(body).jsonObject
    }

    @Then("trace step {int} checked these facts")
    fun checked(step: Int, table: DataTable) {
        val json = steps.run.trace[step - 1].json()
        val facts = (json.getValue("params") as JsonObject).getValue("facts") as JsonArray
        val read = facts.map { fact ->
            listOf("member", "test", "held", "holds").associateWith { (fact as JsonObject).getValue(it).jsonPrimitive.content }
        }
        assertEquals(table.asMaps(), read)
    }

    @Then("trace step {int} drew the check")
    fun drew(step: Int, drawing: String) {
        val params = steps.run.trace[step - 1].json().getValue("params") as JsonObject
        assertEquals(drawing, params["drawing"]?.jsonPrimitive?.content)
    }
}
