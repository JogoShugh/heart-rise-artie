package com.artie.heart.bdd

import com.artie.heart.Acted
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.Rung
import com.artie.heart.fixtures.PullRequest
import com.artie.heart.fixtures.PullRequests
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals

/** A pull request type, whose item path is its owner, its repository and its number. */
class ItemPathSteps(private val world: LampWorld) {
    private val mapper = ObjectMapper()
    private lateinit var layers: Layers<PullRequest, String>
    private val pulls = mutableListOf<PullRequest>()
    private val rendered = mutableListOf<JsonNode>()
    private lateinit var read: JsonNode
    private var refusal: Throwable? = null

    @Given("a pull request type declared with the item path {string}")
    fun declared(template: String) {
        layers = Layers(PullRequests.type(ItemPath(template, PullRequests.POINTERS)))
    }

    @Given("pull request {long} of {string} is open")
    fun open(number: Long, repository: String) {
        val (owner, repo) = repository.split("/")
        pulls += PullRequest(owner, repo, number, "open", version = 1)
    }

    @When("the pull request is rendered at L{int}")
    fun render(rung: Int) {
        rendered.add(tree(layers.layer1(pulls.first(), Rung.entries[rung])))
    }

    @When("both pull requests are rendered at L{int}")
    fun renderBoth(rung: Int) {
        pulls.forEach { rendered.add(tree(layers.layer1(it, Rung.entries[rung]))) }
    }

    @When("its affordance set is read")
    fun readSet() {
        val href = rendered.last().path("_links").path(Layers.FORMS_REL).path("href").asText()
        read = mapper.readTree(layers.affordanceSets.getValue(href.removePrefix(Layers.AFFORDANCE_SETS)))
    }

    @When("the pull request is rendered in HAL-FORMS")
    fun renderHalForms() {
        rendered.add(tree(layers.halForms(pulls.first())))
    }

    @When("the OpenAPI description of the pull request type is rendered")
    fun renderOpenApi() {
        val resource = HeartResource(
            layers = layers,
            loadAt = { error("not read") },
            perform = { pull, _, _ -> Acted(pull) },
        )
        read = mapper.readTree(OpenApiDocument(listOf(resource)).body)
    }

    @When("a pull request type is declared with the item path {string} and no pointer for {string}")
    fun declaredWithout(template: String, variable: String) {
        refusal = runCatching { PullRequests.type(ItemPath(template, PullRequests.POINTERS - variable)) }
            .exceptionOrNull()
    }

    @Then("its self link is {string}")
    fun selfLink(href: String) {
        assertEquals(href, rendered.last().path("_links").path("self").path("href").asText())
    }

    @Then("the form {string} targets {string}")
    fun formTargets(key: String, href: String) {
        assertEquals(href, target(rendered.last(), key).asText())
    }

    @Then("the form {string} targets the template {string}")
    fun formTargetsTemplate(key: String, href: String) {
        assertEquals(href, target(read, key).asText())
    }

    @Then("its template pointers are")
    fun templatePointers(table: DataTable) {
        val expected = table.asMaps().associate { it.getValue("variable") to it.getValue("pointer") }
        val form = read.path("_forms").first()
        val pointers = form.path("hr:templatePointers").properties().associate { it.key to it.value.asText() }
        assertEquals(expected, pointers)
    }

    @Then("they link the same affordance set")
    fun sameSet() {
        val links = rendered.map { it.path("_links").path(Layers.FORMS_REL).path("href").asText() }
        assertEquals(1, links.toSet().size, "links: $links")
    }

    @Then("its template {string} targets {string}")
    fun templateTargets(name: String, href: String) {
        assertEquals(href, rendered.last().path("_templates").path(name).path("target").asText())
    }

    @Then("it has the path {string} with the parameters {string}")
    fun pathWithParameters(path: String, names: String) {
        val parameters = read.path("paths").path(path).path("get").path("parameters").map { it.path("name").asText() }
        assertEquals(names.split(", "), parameters)
    }

    @Then("it has the path {string}")
    fun hasPath(path: String) {
        val paths = read.path("paths").fieldNames().asSequence().toList()
        assertEquals(true, path in paths, "paths: $paths")
    }

    @Then("the declaration is refused, saying {string}")
    fun refused(message: String) {
        assertEquals(message, refusal?.message)
    }

    @Then("the lamp type's item path is {string} with the pointer {string} at {string}")
    fun lampItemPath(template: String, variable: String, pointer: String) {
        assertEquals(template, world.type.itemPath.template)
        assertEquals(mapOf(variable to pointer), world.type.itemPath.pointers)
    }

    private fun target(body: JsonNode, key: String): JsonNode =
        body.path("_forms").path(key).path("_links").path("target").path("href")

    private fun tree(body: Map<String, Any?>): JsonNode = mapper.valueToTree(body)
}
