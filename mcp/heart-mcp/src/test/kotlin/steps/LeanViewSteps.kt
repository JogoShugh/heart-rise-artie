package steps

import heart.AffordanceForm
import heart.HeartRepresentation
import heart.Manifest
import heart.mcp.Views
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What heart-mcp shows of acts it has shown before, and of a state with HEART's own members, in one session. */
class LeanViewSteps(private val shown: HeldView) {
    private val session get() = shown.session

    @Given("heart-mcp holds an employee offering these acts")
    fun employee(table: DataTable) {
        val state = buildJsonObject {
            put("id", 3)
            put("firstName", "Samwise")
            put("role", "gardener")
        }
        shown.held = HeartRepresentation(EMPLOYEE, state, Manifest(forms = forms(table)), "\"seq-1\"")
    }

    @When("the employee now offers these acts")
    fun nowOffers(table: DataTable) {
        val held = shown.held
        shown.held = HeartRepresentation(held.url, held.state, Manifest(forms = forms(table)), "\"seq-2\"")
    }

    @Given("heart-mcp holds a bookmark whose state has a title and an hr:manifest")
    fun bookmark() {
        val state = buildJsonObject {
            put("title", "Employees")
            putJsonObject("hr:manifest") {
                put("digest", "sha256-Ex2wMcTtBBbUXam2gBecq4hHrvBPJXiUEc/mqP+z/Yc=")
            }
        }
        shown.held = HeartRepresentation(BOOKMARK, state, Manifest(), "\"index-1\"")
    }

    @When("heart-mcp shows what it holds again")
    fun showsAgain() {
        shown.view = Views.of(shown.held, shown = session)
    }

    @Then("these acts show their schemas")
    fun schemas(table: DataTable) = table.asMaps().forEach { row ->
        val schema = act(row.getValue("act")).getValue("schema")
        assertTrue(schema is JsonObject && "type" in schema, "${row["act"]}: $schema")
    }

    @Then("these acts show their schemas as before, with their titles")
    fun asBefore(table: DataTable) = table.asMaps().forEach { row ->
        val act = act(row.getValue("act"))
        assertEquals(JsonPrimitive(Views.AS_BEFORE), act["schema"], "$act")
        assertTrue("title" in act, "$act")
    }

    @Then("the view's state has exactly these members")
    fun members(table: DataTable) {
        val state = shown.view.getValue("resource").jsonObject.getValue("state").jsonObject
        assertEquals(table.asMaps().map { it.getValue("member") }, state.keys.toList())
    }

    private fun act(name: String): JsonObject = shown.view.getValue("acts").jsonArray.map { it.jsonObject }
        .single { it.getValue("name").jsonPrimitive.content == name }

    /** Each row's act, taking its one field, or none. */
    private fun forms(table: DataTable): Map<String, AffordanceForm> = table.asMaps().associate { row ->
        val act = row.getValue("act")
        val field = row["field"].orEmpty()
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { if (field.isNotEmpty()) putJsonObject(field) { put("type", "string") } }
            if (field.isNotEmpty()) put("required", buildJsonArray { add(JsonPrimitive(field)) })
        }
        val target = "$EMPLOYEE/actions/${act.substringAfter(':')}"
        act to AffordanceForm(act, target, false, "POST", JSON, schema, title = act.substringAfter(':'))
    }

    private companion object {
        const val EMPLOYEE = "http://service.test/employees/3"
        const val BOOKMARK = "http://service.test/"
        const val JSON = "application/json"
    }
}
