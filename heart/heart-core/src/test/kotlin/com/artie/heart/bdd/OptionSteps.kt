package com.artie.heart.bdd

import com.artie.heart.ActPipeline
import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.Rung
import com.artie.heart.StateETag
import com.artie.heart.UnprocessableContentException
import com.artie.heart.fixtures.Match
import com.artie.heart.fixtures.MatchPlayer
import com.artie.heart.fixtures.Matches
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for options.feature: choices from state, rendered and enforced by heart-core. */
class OptionSteps {
    private val mapper = ObjectMapper()
    private val layers = Layers(Matches.type())
    private val matches = mutableListOf<Match>()
    private lateinit var read: JsonNode
    private var outcome: Result<Unit>? = null

    @Given("a playing match at level {string} with these players")
    @Given("another playing match at level {string} with these players")
    fun match(level: String, table: DataTable) {
        val rows = table.asMaps()
        matches += Match(matches.size + 1L, "PLAYING", level, players(rows, "home"), players(rows, "away"))
    }

    @When("the schema of the act {string} is read")
    fun schemaOf(act: String) {
        read = mapper.readTree(layers.type.act(act).schema)
    }

    @Then("its property {string} carries hr:options:")
    fun carries(property: String, expected: String) = assertEquals(
        ContentAddress.canonicalText(expected),
        ContentAddress.canonicalText(read.path("properties").path(property).path(OPTIONS).toString()),
    )

    @Then("both matches link the same affordance set at L1 and at L2")
    fun sameSet() = listOf(Rung.L1, Rung.L2).forEach { rung ->
        val (first, second) = matches.take(2).map { formsHref(it, rung) }
        assertEquals(first, second, "$rung")
    }

    @When("the match is rendered at L0")
    fun renderedL0() {
        read = mapper.valueToTree(layers.layer1(matches.first(), Rung.L0))
    }

    @Then("its form {string} has a property {string} that carries hr:options")
    fun formCarries(form: String, property: String) = assertTrue(
        read.path("_forms").path(form).path("schema").path("properties").path(property).has(OPTIONS),
        read.path("_forms").path(form).toString(),
    )

    @When("the body {} is checked for the act {string} against the match")
    fun checked(body: String, act: String) {
        outcome = runCatching { runBlocking { ActPipeline(layers.type).run(act, body, matches.first()) { } } }
    }

    @Then("the choices accept the body")
    fun accepted() = checkNotNull(outcome).getOrThrow()

    @Then("the choices refuse the body at {string} as not one of the choices")
    fun refusedAt(pointer: String) {
        val failure = checkNotNull(outcome).exceptionOrNull()
        assertTrue(failure is UnprocessableContentException, "expected 422, got $failure")
        val violation = (failure as UnprocessableContentException).violations.single()
        assertEquals(pointer, violation.pointer)
        assertTrue("not one of the choices" in violation.message, violation.message)
    }

    @When("the match is rendered in HAL-FORMS")
    fun renderedHalForms() {
        read = mapper.valueToTree(layers.halForms(matches.first()))
    }

    @Then("the template {string} property {string} has these inline options")
    fun inlineOptions(template: String, property: String, table: DataTable) {
        val inline = halFormsProperty(template, property).path("options").path("inline")
        val shown = inline.map { mapOf("value" to it.path("value").asText(), "prompt" to it.path("prompt").asText()) }
        assertEquals(table.asMaps(), shown)
    }

    @Then("the template {string} property {string} has an options link {string}")
    fun optionsLink(template: String, property: String, href: String) =
        assertEquals(href, halFormsProperty(template, property).path("options").path("link").path("href").asText())

    @Then("that HAL-FORMS representation has a validator, as every Layer 1 representation does")
    fun validator() {
        val etag = StateETag.of(1, Dialect.HALFORMS.etagTag, Rung.L0, layers.halForms(matches.first()))
        assertTrue(etag.startsWith("\"seq-1.hf.L0."), etag)
    }

    @When("the OpenAPI description of the matches is rendered")
    fun openApi() {
        val resource = HeartResource(
            layers,
            load = { error("not loaded") },
            perform = { _, _, _ -> error("not performed") },
        )
        read = mapper.readTree(OpenApiDocument(listOf(resource)).body)
    }

    @Then("its schema {string} property {string} carries x-heart-options with the pointer {string}")
    fun extension(schema: String, property: String, pointer: String) {
        val options = read.path("components").path("schemas").path(schema).path("properties").path(property)
        assertEquals(pointer, options.path("x-heart-options").path("pointer").asText(), options.toString())
        assertTrue(!options.has(OPTIONS), "the OpenAPI schema keeps hr:options: $options")
    }

    @Then("the description of {string} says {string}")
    fun says(operationId: String, text: String) {
        val operation = read.path("paths").flatMap { it.toList() }
            .first { it.path("operationId").asText() == operationId }
        assertTrue(text in operation.path("description").asText(), operation.path("description").asText())
    }

    private fun formsHref(match: Match, rung: Rung): String =
        mapper.valueToTree<JsonNode>(layers.layer1(match, rung)).path("_links").path("hr:forms").path("href").asText()

    private fun halFormsProperty(template: String, property: String): JsonNode =
        read.path("_templates").path(template).path("properties").first { it.path("name").asText() == property }

    /** "4 Ana, 7 Bea fouled out" -> players. */
    private fun players(rows: List<Map<String, String>>, team: String): List<MatchPlayer> =
        rows.filter { it["team"] == team }.map {
            MatchPlayer(it.getValue("number").toInt(), it.getValue("name"), fouledOut = it["fouled out"] == "yes")
        }

    private companion object {
        const val OPTIONS = "hr:options"
    }
}
