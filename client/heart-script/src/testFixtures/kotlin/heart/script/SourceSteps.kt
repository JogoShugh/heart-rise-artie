package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** Steps for where values came from (value-sources.feature), read from the trace as a person would. */
class SourceSteps(private val steps: ScriptSteps) {

    @When("the script runs unattended, allowed a reasoner, given")
    fun unattendedReasoning(table: DataTable) = steps.runUnattended(table, reasoning = true)

    @Then("trace step {int}'s values came from")
    fun valuesFrom(step: Int, table: DataTable) {
        val values = steps.run.trace[step - 1].json()["values"]?.jsonObject
        val read = table.asMaps().map { row ->
            val field = row.getValue("field")
            val value = values?.get(field)?.jsonObject
            mapOf("field" to field, "value" to value?.get("value")?.let(::text), "from" to value?.get("from")?.let(::sources))
        }
        assertEquals(table.asMaps(), read)
    }

    @Then("trace step {int}'s reason template is {string}, with these slots")
    fun template(step: Int, template: String, table: DataTable) {
        val reason = steps.run.trace[step - 1].json()["reason"]?.jsonObject
        assertEquals(template, reason?.get("template")?.let(::text))
        val slots = reason?.get("slots")?.jsonObject
        val read = table.asMaps().map { row ->
            val slot = slots?.get(row.getValue("slot"))?.jsonObject
            mapOf("slot" to row.getValue("slot"), "value" to slot?.get("value")?.let(::text),
                "from" to slot?.get("from")?.let(::sources))
        }
        assertEquals(table.asMaps(), read)
    }

    @Then("the run's inputs are")
    fun inputs(table: DataTable) {
        val inputs = kotlinx.serialization.json.Json.parseToJsonElement(steps.run.traceJson()).jsonObject["inputs"]
            ?.jsonObject
        val read = table.asMaps().map { row ->
            val input = inputs?.get(row.getValue("input"))?.jsonObject
            mapOf("input" to row.getValue("input"), "value" to input?.get("value")?.let(::text),
                "given" to input?.get("given")?.let(::text))
        }
        assertEquals(table.asMaps(), read)
    }

    private fun text(node: JsonElement): String = (node as? JsonPrimitive)?.content ?: node.toString()

    /** A value's sources, in words: "input crop", "step 2 /number", "words", "constant", "parts: 7 from …". */
    private fun sources(node: JsonElement): String = (node as? JsonArray).orEmpty().joinToString(", ") { source ->
        when {
            source is JsonPrimitive -> source.content
            "input" in source.jsonObject -> "input ${text(source.jsonObject.getValue("input"))}"
            "step" in source.jsonObject -> "step ${text(source.jsonObject.getValue("step"))} " +
                text(source.jsonObject.getValue("pointer"))
            "parts" in source.jsonObject -> "parts: " + source.jsonObject.getValue("parts").jsonObject.entries
                .joinToString("; ") { (part, from) -> "$part from ${sources(JsonArray(listOf(from)))}" }
            else -> source.toString()
        }
    }
}
