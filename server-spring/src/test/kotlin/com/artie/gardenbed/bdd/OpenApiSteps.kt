package com.artie.gardenbed.bdd

import com.artie.gardenbed.declaration.GardenBeds
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * The garden's OpenAPI description, checked against the copy published beside
 * the exhibit. With -Popenapi.write=true the served bytes are written there
 * instead, pretty-printed for people to read and diff.
 */
class OpenApiSteps(private val http: HttpWorld) {
    private val mapper = ObjectMapper()
    private val description: JsonNode get() = http.last.json

    @When("a client gets the OpenAPI description")
    fun get() {
        http.get("/openapi.json")
        assertEquals(200, http.last.status, String(http.last.bytes))
    }

    @Then("it is the published {string}")
    fun published(name: String) {
        val file = File(PUBLISHED, name)
        if (System.getProperty("openapi.write") == "true") {
            file.parentFile.mkdirs()
            file.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(description) + "\n")
        }
        assertTrue(file.exists(), "no published copy at ${file.absolutePath}; run with -Popenapi.write=true")
        // The published copy is pretty-printed; served bytes are canonical, so compare as JSON.
        assertEquals(mapper.readTree(file), description, "the served description differs from $name")
    }

    @Then("its info version is {string}")
    fun version(version: String) = assertEquals(version, description.path("info").path("version").asText())

    @Then("its {string} status enum is")
    fun statusEnum(schema: String, table: DataTable) {
        val enum = description.path("components").path("schemas").path(schema).path("properties").path("status")
            .path("enum").map { it.asText() }
        assertEquals(table.asMaps().map { it.getValue("status") }, enum)
    }

    @Then("its operations include {string}")
    fun includes(operation: String) {
        val (method, path) = operation.split(" ")
        assertTrue(description.path("paths").path(path).has(method.lowercase()), "no $operation")
    }

    @Then("the description of {string} ends {string}")
    fun descriptionEnds(operationId: String, end: String) {
        val text = operation(operationId).path("description").asText()
        assertTrue(text.endsWith(end), text)
    }

    @Then("the operation {string} takes the schema {string}, which is the {word} form's schema")
    fun takesFormSchema(operationId: String, schema: String, actPath: String) {
        val ref = operation(operationId).path("requestBody").path("content").path("application/json")
            .path("schema").path("\$ref").asText()
        assertEquals("#/components/schemas/$schema", ref)
        val act = GardenBeds.type(frost = false).acts.first { it.path == actPath }
        assertEquals(mapper.readTree(act.schema), description.path("components").path("schemas").path(schema))
    }

    private fun operation(operationId: String): JsonNode =
        description.path("paths").flatMap { item -> item.toList() }
            .first { it.path("operationId").asText() == operationId }

    private companion object {
        /** Beside the exhibit, from server-spring's directory, where its tests run. */
        val PUBLISHED = File("../exhibits/openapi-vs-heart/openapi")
    }
}
