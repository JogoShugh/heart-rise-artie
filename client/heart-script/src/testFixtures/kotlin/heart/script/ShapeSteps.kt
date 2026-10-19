package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for a run's shape in its trace (run-shape.feature), read from the trace file as written. */
class ShapeSteps(private val steps: ScriptSteps) {

    @Then("the trace's steps are shaped")
    fun shaped(table: DataTable) {
        val read = traced().mapIndexed { i, step ->
            val waited = step["waited"] as? JsonObject
            mapOf(
                "step" to "${i + 1}",
                "does" to step.getValue("say").jsonPrimitive.content.split(' ', ',').first(),
                "line" to step["line"]?.jsonPrimitive?.content.orEmpty(),
                "opens" to if (step["opens"]?.jsonPrimitive?.content == "true") "yes" else "",
                "waited for" to waited?.get("for")?.jsonPrimitive?.content.orEmpty(),
                "reads" to waited?.get("reads")?.jsonPrimitive?.content.orEmpty(),
            )
        }
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value.orEmpty() } }, read)
    }

    @Then("trace step {int} picked its link by {string}")
    fun picked(step: Int, by: String) =
        assertEquals(by, (traced()[step - 1]["chose"] as? JsonObject)?.get("picked")?.jsonPrimitive?.content)

    @Then("trace step {int} is a judgement")
    fun judgement(step: Int) = assertEquals("true", traced()[step - 1]["judgement"]?.jsonPrimitive?.content)

    @Then("trace step {int} keeps these members for later steps")
    fun keeps(step: Int, table: DataTable) {
        val keeps = traced()[step - 1]["keeps"] as? JsonObject
        val read = table.asMaps().map { row ->
            val by = (keeps?.get(row.getValue("pointer")) as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content }
            mapOf("pointer" to row.getValue("pointer"), "read by" to by)
        }
        assertEquals(table.asMaps(), read)
    }

    /** The trace's steps, as the trace file has them: some members are known only once the run is over. */
    private fun traced(): List<JsonObject> =
        (Json.parseToJsonElement(steps.run.traceJson()).jsonObject.getValue("steps") as JsonArray).map { it.jsonObject }
}
