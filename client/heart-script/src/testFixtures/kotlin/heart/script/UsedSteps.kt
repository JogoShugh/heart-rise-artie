package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for the affordance a step used and what was offered (chosen-affordances.feature), read from the trace. */
class UsedSteps(private val steps: ScriptSteps) {

    @Given("the form {string} at {string} asks for these fields")
    fun fields(relation: String, at: String, table: DataTable) {
        steps.service.fields["$relation at $at"] = table.asMaps().map { it.getValue("field") }
    }

    @Then("trace step {int} used")
    fun used(step: Int, table: DataTable) = assertEquals(
        rows(table),
        rows(table).keys.associateWith { what -> text(json(step)["chose"], what) },
    )

    @Then("trace step {int}'s guard is")
    fun guard(step: Int, table: DataTable) = assertEquals(
        rows(table),
        rows(table).keys.associateWith { what -> text(json(step)["guard"], what) },
    )

    @Then("trace step {int} was offered")
    fun offered(step: Int, table: DataTable) {
        val offered = json(step)["offered"] as? JsonObject
        val read = listOf("forms" to "form", "links" to "link").flatMap { (group, kind) ->
            (offered?.get(group) as? JsonArray).orEmpty().map { mapOf("kind" to kind, "relation" to it.jsonPrimitive.content) }
        }
        assertEquals(table.asMaps(), read)
    }

    private fun json(step: Int): JsonObject = steps.run.trace[step - 1].json()

    private fun rows(table: DataTable): Map<String, String> =
        table.asMaps().associate { it.getValue("what") to it.getValue("value") }

    /** A member of [node] as text: a list as its items, comma-separated, as the table writes them. */
    private fun text(node: JsonElement?, what: String): String? = when (val value = (node as? JsonObject)?.get(what)) {
        null -> null
        is JsonArray -> value.joinToString(", ") { it.jsonPrimitive.content }
        is JsonPrimitive -> value.content
        else -> value.toString()
    }
}
