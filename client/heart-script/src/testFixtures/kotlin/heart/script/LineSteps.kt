package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for the lines a trace names (script-lines.feature): each step's line, and its block's. */
class LineSteps(private val steps: ScriptSteps) {

    @Then("the trace's steps name these lines")
    fun lines(table: DataTable) {
        val read = steps.run.trace.mapIndexed { i, step ->
            val json = step.json()
            mapOf(
                "step" to "${i + 1}",
                "does" to step.asked.say.split(' ', ',').first(),
                "line" to json["line"]?.jsonPrimitive?.content.orEmpty(),
                "within" to json["within"]?.jsonPrimitive?.content.orEmpty(),
            )
        }
        // An empty cell reads as null: a step with no block around it.
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value.orEmpty() } }, read)
    }
}
