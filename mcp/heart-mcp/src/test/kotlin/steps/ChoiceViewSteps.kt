package steps

import heart.AffordanceForm
import heart.HeartRepresentation
import heart.Manifest
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What heart-mcp shows of a form whose choices come from state (HAL-SCHEMA-FORMS-HR Rule 8). */
class ChoiceViewSteps(private val shown: HeldView) {

    @Given("heart-mcp holds a game whose home team's players are")
    fun game(table: DataTable) {
        val players = buildJsonArray {
            table.asMaps().forEach { row ->
                addJsonObject {
                    put("number", row.getValue("number").toInt())
                    put("name", row.getValue("name"))
                    put("fouledOut", row.getValue("fouledOut").toBoolean())
                }
            }
        }
        val state = buildJsonObject { putJsonObject("home") { put("players", players) } }
        shown.held = HeartRepresentation(GAME, state, Manifest(), "\"seq-1\"")
    }

    @Given("the game offers {string}, whose playerNumber is one of {string} not fouled out")
    fun offersFromState(act: String, pointer: String) = offer(act, "playerNumber", "integer") {
        put("pointer", pointer)
        put("valueField", "number")
        put("promptField", "name")
        putJsonObject("where") { put("fouledOut", false) }
    }

    @Given("the agents resource lists")
    fun agents(table: DataTable) {
        shown.readable[AGENTS] = buildJsonObject {
            put("agents", buildJsonArray {
                table.asMaps().forEach { row ->
                    addJsonObject {
                        put("name", row.getValue("name"))
                        put("onDuty", row.getValue("onDuty").toBoolean())
                    }
                }
            })
        }
    }

    @Given("the game offers {string}, whose agent is one of the agents on duty")
    fun offersFromLink(act: String) = offer(act, "agent", "string") {
        putJsonObject("link") { put("href", "/agents") }
        put("items", "/agents")
        put("valueField", "name")
        put("promptField", "name")
        putJsonObject("where") { put("onDuty", true) }
    }

    @Then("the act {string} shows {word} as one of")
    fun showsChoices(act: String, property: String, table: DataTable) {
        val schema = shown.view.getValue("acts").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == act }.getValue("schema").jsonObject
        val shownProperty = schema.getValue("properties").jsonObject.getValue(property).jsonObject
        val choices = shownProperty["enum"]?.jsonArray.orEmpty()
        assertEquals(table.asMaps().map { it.getValue("value") }, choices.map { it.jsonPrimitive.content }, "$schema")
        val description = shownProperty["description"]?.jsonPrimitive?.content.orEmpty()
        // Each choice is described as its value in JSON, then its prompt.
        table.asMaps().zip(choices).forEach { (row, value) ->
            val choice = "$value (${row.getValue("prompt")})"
            assertTrue(choice in description, "'$choice' is not in the description: $description")
        }
    }

    @Then("no act's schema in the view says {string}")
    fun noKeyword(keyword: String) =
        assertFalse(shown.view.getValue("acts").toString().contains(keyword), "$keyword in ${shown.view}")

    /** The held game, now also offering [act], whose [property] takes its choices as [options] says. */
    private fun offer(act: String, property: String, type: String, options: JsonObjectBuilder.() -> Unit) {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject(property) {
                    put("type", type)
                    put("hr:options", buildJsonObject(options))
                }
            }
            put("required", buildJsonArray { add(JsonPrimitive(property)) })
        }
        val form = AffordanceForm(act, "$GAME/actions/${act.substringAfter(':')}", false, "POST", JSON, schema)
        val held = shown.held
        val manifest = held.manifest.copy(forms = held.manifest.forms + (act to form))
        shown.held = HeartRepresentation(held.url, held.state, manifest, held.etag)
    }

    private companion object {
        const val GAME = "http://service.test/games/1"
        const val AGENTS = "http://service.test/agents"
        const val JSON = "application/json"
    }
}
