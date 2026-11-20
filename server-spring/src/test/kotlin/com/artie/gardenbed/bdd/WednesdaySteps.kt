package com.artie.gardenbed.bdd

import com.artie.gardenbed.declaration.GardenBeds
import com.artie.gardenbed.declaration.GardenChange
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.Rung
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
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
    private lateinit var description: JsonNode

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

    @Then("under each change, a planted bed's water form requires exactly")
    fun requires(table: DataTable) = byChange(table) { change, rows ->
        val required = form().path("schema").path("required").map { it.asText() }.sorted()
        assertEquals(rows.map { it.getValue("field") }.sorted(), required, change)
    }

    @Then("its state names its moisture {string}")
    fun moisture(member: String) =
        assertTrue(bed.has(member), "state members: ${bed.fieldNames().asSequence().toList()}")

    @Then("the water form's {string} is one of these, for example {string}")
    fun choices(field: String, example: String, table: DataTable) {
        val property = form().path("schema").path("properties").path(field)
        assertEquals(table.asMaps().map { it.getValue("value") }, property.path("enum").map { it.asText() })
        assertEquals(example, property.path("examples").first().asText())
    }

    @Then("under each change, a planted bed's state has the members")
    fun members(table: DataTable) = byChange(table) { change, rows ->
        val members = bed.fieldNames().asSequence().toList()
        rows.map { it.getValue("member") }.forEach { assertTrue(it in members, "$change: state members: $members") }
    }

    /** Each change's rows of [table], checked by [check] against a planted bed rendered under that change. */
    private fun byChange(table: DataTable, check: (String, List<Map<String, String>>) -> Unit) =
        table.asMaps().groupBy { it.getValue("change") }.forEach { (change, rows) ->
            declared(change)
            rendered(2)
            check(change, rows)
        }

    @Then("the water form's {string} is one of these, defaulting to {string}")
    fun defaulting(field: String, default: String, table: DataTable) {
        val property = form().path("schema").path("properties").path(field)
        assertEquals(table.asMaps().map { it.getValue("value") }, property.path("enum").map { it.asText() })
        assertEquals(default, property.path("default").asText())
    }

    @When("the garden's OpenAPI description is rendered")
    fun describe() {
        // Only the description is read here, so the resource never loads or performs anything.
        val resource = HeartResource(
            checkNotNull(layers),
            load = { error("not loaded") },
            perform = { _, _, _ -> error("not performed") },
            performAtBookmark = { _, _ -> error("not performed") },
        )
        description = ObjectMapper().readTree(OpenApiDocument(listOf(resource)).body)
    }

    @Then("its operation {string} is at {string}")
    fun operationAt(operationId: String, path: String) = assertEquals(path, operationPath(operationId))

    @Then("its operation {string} is at {string}, deprecated")
    fun deprecatedAt(operationId: String, path: String) {
        assertEquals(path, operationPath(operationId))
        assertTrue(description.path("paths").path(path).path("post").path("deprecated").asBoolean())
    }

    @Then("the bed's member {string} is deprecated, and {string} is not")
    fun deprecatedMember(old: String, new: String) {
        val properties = description.path("components").path("schemas").path("Bed").path("properties")
        assertTrue(properties.path(old).path("deprecated").asBoolean(), "$old: ${properties.path(old)}")
        assertTrue(properties.has(new) && !properties.path(new).has("deprecated"), "$new: ${properties.path(new)}")
    }

    private fun operationPath(operationId: String): String? =
        description.path("paths").fields().asSequence()
            .firstOrNull { (_, item) -> item.path("post").path("operationId").asText() == operationId }?.key

    @Then("the declaration fails with {string}")
    fun fails(text: String) {
        val error = checkNotNull(failure) { "expected the declaration to fail" }
        assertEquals(text, error.message)
    }

    private fun form(): JsonNode = bed.path("_forms").elements().next()
}
