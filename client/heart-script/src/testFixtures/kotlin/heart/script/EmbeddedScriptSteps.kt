package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals

/** Steps for embedded resources (SPEC §6.1): what the scripted service embeds, and what the trace kept. */
class EmbeddedScriptSteps(private val steps: ScriptSteps) {

    @Given("these embedded resources")
    fun embedded(table: DataTable) = table.asMaps().forEach {
        val resource = it.getValue("resource")
        val title = steps.service.resources.getValue(resource)["title"]
        steps.service.embeds += Link(it.getValue("at"), it.getValue("relation"), title, resource)
    }

    @Then("trace step {int} embeds these resources")
    fun traceEmbeds(step: Int, table: DataTable) {
        val now = checkNotNull(steps.run.trace[step - 1].now) { "trace step $step holds nothing" }
        val json = steps.run.trace[step - 1].json()
        val embedded = now.embedded.flatMap { (rel, list) -> list.map { mapOf("relation" to rel, "title" to it.title) } }
        assertEquals(table.asMaps(), embedded)
        assertEquals(now.embedded.keys, json.getValue("now").let { it as kotlinx.serialization.json.JsonObject }
            .getValue("embedded").let { it as kotlinx.serialization.json.JsonObject }.keys)
    }
}
