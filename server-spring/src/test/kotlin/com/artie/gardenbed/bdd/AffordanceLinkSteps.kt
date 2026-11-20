package com.artie.gardenbed.bdd

import com.artie.gardenbed.config.HeartConfig
import com.artie.gardenbed.declaration.GardenBeds
import com.artie.heart.HeartResource
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for the garden's affordance links: its configuration, built without a server. */
class AffordanceLinkSteps {
    private var limits = ServingLimits()
    private lateinit var description: JsonNode

    @Given("the garden serves affordance links")
    fun linked() {
        limits = ServingLimits(affordanceLinks = true)
    }

    @When("the garden's OpenAPI description is built")
    fun built() {
        // Only the description is read here, so the resource never loads or performs anything.
        val resource = HeartResource(
            GardenBeds.layers(),
            load = { error("not loaded") },
            perform = { _, _, _ -> error("not performed") },
            performAtBookmark = { _, _ -> error("not performed") },
        )
        val document = HeartConfig(frost = false, changeName = "").openApiDocument(resource, limits)
        description = ObjectMapper().readTree(document.body)
    }

    @Then("the built description's version is {string}")
    fun version(version: String) = assertEquals(version, description.path("info").path("version").asText())

    @Then("the built description documents the Link header on these operations")
    fun linkHeader(table: DataTable) = table.asMaps().map { it.getValue("operation") }.forEach { operationId ->
        val operation = description.path("paths").flatMap { item -> item.toList() }
            .first { it.path("operationId").asText() == operationId }
        assertTrue(operation.path("responses").path("200").path("headers").has("Link"), "$operationId: $operation")
    }

    @Then("the built description names the affordance relation {string}")
    fun relation(rel: String) = assertEquals(rel, description.path("x-heart-affordances").path("rel").asText())
}
