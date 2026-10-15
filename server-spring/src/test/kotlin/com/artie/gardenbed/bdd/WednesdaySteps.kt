package com.artie.gardenbed.bdd

import com.artie.gardenbed.declaration.GardenBeds
import com.artie.gardenbed.declaration.GardenChange
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.Layers
import com.artie.heart.Rung
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for wednesday.feature: what each declared change renders, without a server. */
class WednesdaySteps {
    private var layers: Layers<GardenBedState, GardenBedStatus>? = null
    private var failure: Throwable? = null
    private lateinit var bed: JsonNode

    // Cucumber matches the step's text, not its keyword: this one serves Given and When.
    @Given("the garden is declared with the change {string}")
    fun declared(change: String) {
        val result = runCatching { GardenBeds.layers(change = GardenChange.of(change.takeUnless { it == "none" })) }
        layers = result.getOrNull()
        failure = result.exceptionOrNull()
    }

    @When("a planted bed {long} is rendered at L0")
    fun rendered(id: Long) {
        val planted = GardenBedState(id, "bed-$id", 4, 8, GardenBedStatus.PLANTED, "basil", 80, 40)
        bed = ObjectMapper().valueToTree(checkNotNull(layers).layer1(planted, Rung.L0))
    }

    @Then("it offers exactly the form {string}")
    fun offers(relation: String) = assertEquals(listOf(relation), bed.path("_forms").fieldNames().asSequence().toList())

    @Then("that form is titled {string} and targets {string}")
    fun titledAndTargets(title: String, target: String) {
        val form = form()
        assertEquals(title, form.path("title").asText())
        assertEquals(target, form.path("_links").path("target").path("href").asText())
    }

    @Then("its milliliters may be at most {int}")
    fun max(max: Int) =
        assertEquals(max, form().path("schema").path("properties").path("milliliters").path("maximum").asInt())

    @Then("its body requires {string}")
    fun requires(fields: String) {
        val required = form().path("schema").path("required").map { it.asText() }.sorted()
        assertEquals(fields.split(",").map { it.trim() }.sorted(), required)
    }

    @Then("its state names its moisture {string}")
    fun moisture(member: String) =
        assertTrue(bed.has(member), "state members: ${bed.fieldNames().asSequence().toList()}")

    @Then("the water form's {string} is one of {string}, for example {string}")
    fun choices(field: String, values: String, example: String) {
        val property = form().path("schema").path("properties").path(field)
        assertEquals(values.split(",").map { it.trim() }, property.path("enum").map { it.asText() })
        assertEquals(example, property.path("examples").first().asText())
    }

    @Then("the declaration fails with {string}")
    fun fails(text: String) {
        val error = checkNotNull(failure) { "expected the declaration to fail" }
        assertEquals(text, error.message)
    }

    private fun form(): JsonNode = bed.path("_forms").elements().next()
}
