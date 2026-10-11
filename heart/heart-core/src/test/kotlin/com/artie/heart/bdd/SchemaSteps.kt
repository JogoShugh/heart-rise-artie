package com.artie.heart.bdd

import com.artie.heart.BodyCheck
import com.artie.heart.ContentAddress
import com.artie.heart.InputSchemas
import com.artie.heart.MalformedActBodyException
import com.artie.heart.UnprocessableContentException
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.junit.jupiter.api.Assertions.assertEquals

@Schema(title = "Dim")
data class Dim(
    @field:NotNull @field:Min(1) @field:Max(100)
    @field:Schema(title = "Brightness", description = "Percent of full brightness", examples = ["50"])
    val brightness: Int? = null,
    // Optional, but not empty when given; @NotBlank would also make it required.
    @field:Size(min = 1)
    @field:Schema(title = "Label", examples = ["reading"])
    val label: String? = null,
)

@Schema(title = "Switch off")
class SwitchOff

class SchemaSteps {
    private val inputs = mapOf("Dim" to Dim::class.java, "SwitchOff" to SwitchOff::class.java)
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
    fun refused(pointers: String) {
        val failure = checkNotNull(outcome).exceptionOrNull()
        check(failure is UnprocessableContentException) { "expected 422, got $outcome" }
        assertEquals(pointers, failure.violations.map { it.pointer }.distinct().joinToString(", "))
    }

    @Then("the body is malformed")
    fun malformed() {
        val failure = checkNotNull(outcome).exceptionOrNull()
        check(failure is MalformedActBodyException) { "expected 400, got $outcome" }
    }
}
