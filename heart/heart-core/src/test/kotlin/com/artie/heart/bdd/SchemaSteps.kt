package com.artie.heart.bdd

import com.artie.heart.BodyCheck
import com.artie.heart.ContentAddress
import com.artie.heart.InputSchemas
import com.artie.heart.MalformedActBodyException
import com.artie.heart.UnprocessableContentException
import com.artie.heart.fixtures.Code
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.Glow
import com.artie.heart.fixtures.Rename
import com.artie.heart.fixtures.SwitchOff
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals

class SchemaSteps {
    private val inputs = mapOf(
        "Dim" to Dim::class.java,
        "SwitchOff" to SwitchOff::class.java,
        "Rename" to Rename::class.java,
        "Code" to Code::class.java,
        "Glow" to Glow::class.java,
    )
    private var schema: String = ""
    private var outcome: Result<Any?>? = null

    @When("the schema of the input class {string} is generated")
    fun generate(name: String) {
        schema = InputSchemas.of(inputs.getValue(name))
    }

    @Then("the schema is:")
    fun theSchemaIs(expected: String) =
        assertEquals(ContentAddress.canonicalText(expected), ContentAddress.canonicalText(schema))

    @When("^the body (.*) is checked against the schema of \"(.+)\"$")
    fun check(body: String, name: String) {
        val check = BodyCheck(InputSchemas.of(inputs.getValue(name)))
        outcome = runCatching { check.check(body.ifBlank { null }) }
    }

    @Then("the body is accepted")
    fun accepted() {
        checkNotNull(outcome).getOrThrow()
    }

    @Then("the body is refused at {string}")
    fun refused(pointer: String) = refusedAt(listOf(pointer))

    @Then("the body is refused at these pointers")
    fun refusedAtThese(table: DataTable) = refusedAt(table.asMaps().map { it.getValue("pointer") })

    private fun refusedAt(pointers: List<String>) {
        val failure = checkNotNull(outcome).exceptionOrNull()
        check(failure is UnprocessableContentException) { "expected 422, got $outcome" }
        assertEquals(pointers, failure.violations.map { it.pointer }.distinct())
    }

    @Then("the body is malformed")
    fun malformed() {
        val failure = checkNotNull(outcome).exceptionOrNull()
        check(failure is MalformedActBodyException) { "expected 400, got $outcome" }
    }
}
