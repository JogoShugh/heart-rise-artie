package com.artie.heart.bdd

import com.artie.heart.Acted
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.Past
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SpecVersion
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.MessageDigest

class OpenApiSteps(private val world: LampWorld) {
    private val mapper = ObjectMapper()
    private var keepsPast = false
    private val renderings = mutableListOf<OpenApiDocument>()
    private val document get() = renderings.last()
    private val json: JsonNode get() = mapper.readTree(document.body)

    @Given("the lamp keeps its past")
    fun keepsPast() {
        keepsPast = true
    }

    @When("^the OpenAPI description of the lamp is rendered at version \"([^\"]*)\"(?: again)?$")
    fun rendered(version: String) {
        renderings += OpenApiDocument(listOf(lampResource()), version = version)
    }

    @When("the OpenAPI description of the lamp is rendered at version {string} with affordance links")
    fun renderedWithLinks(version: String) {
        renderings += OpenApiDocument(listOf(lampResource()), version = version, affordanceLinks = true)
    }

    @Then("the operation {string} answers {int} with no header {string}")
    fun answersWithoutHeader(operationId: String, status: Int, header: String) {
        val response = resolve(operation(operationId).path("responses").path(status.toString()))
        assertTrue(!response.path("headers").has(header), "headers: ${response.path("headers")}")
    }

    @Then("it validates against the OpenAPI 3.1 schema")
    fun validOpenApi() {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        val schema = factory.getSchema(SchemaLocation.of("classpath:openapi/schema-2022-10-07.json"))
        val failures = schema.validate(json)
        assertTrue(failures.isEmpty(), "not OpenAPI 3.1: $failures")
    }

    @Then("every schema in its components is a valid JSON Schema 2020-12 schema")
    fun validSchemas() {
        val meta = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"))
        json.path("components").path("schemas").fields().forEach { (name, schema) ->
            val failures = meta.validate(schema)
            assertTrue(failures.isEmpty(), "$name is not JSON Schema 2020-12: $failures")
        }
    }

    @Then("its {string} is {string}")
    fun member(path: String, value: String) =
        assertEquals(value, path.split('.').fold(json) { at, key -> at.path(key) }.asText())

    @Then("the two renderings are identical bytes")
    fun identical() = assertEquals(renderings[renderings.size - 2].body, document.body)

    @Then("the two renderings are not identical bytes")
    fun notIdentical() = assertNotEquals(renderings[renderings.size - 2].body, document.body)

    @Then("its validator is {string} and the first 16 hex digits of its body's digest")
    fun validator(prefix: String) {
        val hex = MessageDigest.getInstance("SHA-256").digest(document.body.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(HEX)
        assertEquals("\"$prefix$hex\"", document.validator)
    }

    @Then("its operations are exactly")
    fun operations(table: DataTable) {
        val expected = table.asMaps().map { row ->
            Triple(row.getValue("method"), row.getValue("path"), row.getValue("operationId"))
        }
        val actual = json.path("paths").fields().asSequence().flatMap { (path, item) ->
            item.fields().asSequence().filter { it.key in METHODS }.map { (method, operation) ->
                Triple(method.uppercase(), path, operation.path("operationId").asText())
            }
        }.toList()
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals(expected.size, actual.size, "$actual")
    }

    @Then("the operations name their relations")
    fun relations(table: DataTable) = table.asMaps().forEach { row ->
        val operationId = row.getValue("operationId")
        assertEquals(row.getValue("relation"), operation(operationId).path("x-heart-relation").asText(), operationId)
    }

    @Then("no operation names a HEART relation")
    fun noRelations() {
        val named = json.path("paths").flatMap { item -> item.toList() }.filter { it.has("x-heart-relation") }
        assertEquals(emptyList<JsonNode>(), named)
    }

    @Then("the operation {string} takes a required application\\/json body {string}")
    fun body(operationId: String, ref: String) {
        val body = operation(operationId).path("requestBody")
        assertTrue(body.path("required").asBoolean(), "$body")
        assertEquals(ref, body.path("content").path("application/json").path("schema").path("\$ref").asText())
    }

    @Then("the component {string} is the schema of the act {string}")
    fun componentIsActSchema(name: String, act: String) {
        val declared = mapper.readTree(world.type.act(act).schema)
        assertEquals(declared, json.path("components").path("schemas").path(name))
    }

    @Then("the description of {string} ends {string}")
    fun descriptionEnds(operationId: String, end: String) {
        val description = operation(operationId).path("description").asText()
        assertTrue(description.endsWith(end), description)
    }

    @Then("the operation {string} is deprecated")
    fun deprecated(operationId: String) = assertTrue(operation(operationId).path("deprecated").asBoolean())

    @Then("the operation {string} is not deprecated")
    fun notDeprecated(operationId: String) = assertTrue(!operation(operationId).has("deprecated"))

    @Then("the summary of {string} is {string}")
    fun summary(operationId: String, summary: String) =
        assertEquals(summary, operation(operationId).path("summary").asText())

    @Then("the operation {string} answers {int} with {string}")
    fun answers(operationId: String, status: Int, ref: String) {
        val response = resolve(operation(operationId).path("responses").path(status.toString()))
        assertEquals(ref, response.path("content").path("application/json").path("schema").path("\$ref").asText())
    }

    @Then("the operation {string} answers {int} with the header {string}")
    fun answersWithHeader(operationId: String, status: Int, header: String) {
        val response = resolve(operation(operationId).path("responses").path(status.toString()))
        assertTrue(response.path("headers").has(header), "headers: ${response.path("headers")}")
    }

    @Then("the component {string} has the property {string} with the enum")
    fun enumOf(name: String, property: String, table: DataTable) {
        val enum = json.path("components").path("schemas").path(name).path("properties").path(property).path("enum")
        assertEquals(table.asMaps().map { it.getValue("value") }, enum.map { it.asText() })
    }

    @Then("the operation {string} documents the responses")
    fun responses(operationId: String, table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("status") }.toSet(),
        operation(operationId).path("responses").fieldNames().asSequence().toSet(),
    )

    @Then("the operation {string} has no {string} parameter")
    fun noParameter(operationId: String, name: String) =
        assertFalse(operation(operationId).path("parameters").any { it.path("name").asText() == name })

    @Then("its security schemes are")
    fun securitySchemes(table: DataTable) {
        val expected = table.asMaps()
            .associate { it.getValue("name") to (it.getValue("type") to it.getValue("scheme")) }
        val schemes = json.path("components").path("securitySchemes")
        assertEquals(expected.keys, schemes.fieldNames().asSequence().toSet())
        expected.forEach { (name, scheme) ->
            val declared = schemes.path(name)
            assertEquals(scheme, declared.path("type").asText() to declared.path("scheme").asText())
        }
        val required = json.path("security").flatMap { it.fieldNames().asSequence().toList() }.toSet()
        assertEquals(expected.keys, required)
    }

    private fun operation(operationId: String): JsonNode =
        json.path("paths").flatMap { item -> METHODS.map { item.path(it) } }
            .firstOrNull { it.path("operationId").asText() == operationId }
            ?: error("no operation $operationId")

    /** A response, followed through its `$ref` into components when it has one. */
    private fun resolve(node: JsonNode): JsonNode {
        val ref = node.path("\$ref").asText()
        return if (ref.isEmpty()) node else ref.removePrefix("#/").split('/').fold(json) { at, key -> at.path(key) }
    }

    /** The lamp as a server registers it. The description reads only its declaration and whether it keeps a past. */
    private fun lampResource(): HeartResource<Lamp, String> = HeartResource(
        layers = Layers(world.type),
        load = { error("not read") },
        perform = { lamp, _, _ -> Acted(lamp) },
        find = { _, _ -> emptyList() },
        past = if (keepsPast) Past(at = { _, _ -> error("not read") }, history = { emptyList() }) else null,
        performAtBookmark = { _, _ -> error("not acted") },
    )

    private companion object {
        const val HEX = 16
        val METHODS = listOf("get", "put", "post", "delete", "patch")
    }
}
