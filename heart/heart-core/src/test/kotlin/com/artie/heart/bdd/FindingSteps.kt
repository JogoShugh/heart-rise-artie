package com.artie.heart.bdd

import com.artie.heart.Acted
import com.artie.heart.Dialect
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.ResourceType
import com.artie.heart.Rung
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
import com.artie.heart.fixtures.PullRequest
import com.artie.heart.fixtures.PullRequests
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** A pull request type found as GitHub lists them: by the fields it declares. */
class FindingSteps {
    private val mapper = ObjectMapper()
    private lateinit var type: ResourceType<PullRequest, String>
    private lateinit var read: JsonNode
    private lateinit var form: JsonNode
    private var repository = "" to ""
    private var state = ""
    private var found = emptyList<Found>()
    private var refusal: Throwable? = null

    @Given("a pull request type found at {string}, filtered by {string}")
    fun found(collectionPath: String, filter: String) {
        type = PullRequests.type(finding = PullRequests.finding(collectionPath))
        check(PullRequests.FIELDS.any { it.name == filter })
    }

    @When("the pull requests' service index is rendered")
    fun index() {
        read = mapper.readTree(ServiceIndex(listOf(type)).body(Dialect.JSONSCHEMA))
    }

    @When("the pull requests' service index is rendered in HAL-FORMS")
    fun halFormsIndex() {
        read = mapper.readTree(ServiceIndex(listOf(type)).body(Dialect.HALFORMS))
    }

    @When("the pull requests' home document is rendered")
    fun home() {
        read = mapper.readTree(ServiceIndex(listOf(type)).home(Rung.L0))
    }

    @Then("the index form {string} targets the template {string}")
    fun targets(key: String, href: String) {
        form = read.path("_forms").path(key)
        assertEquals(href, form.path("_links").path("target").path("href").asText())
        assertEquals(true, form.path("_links").path("target").path("templated").asBoolean())
    }

    @Then("its fields are {string}, of which {string} are required")
    fun fields(names: String, required: String) {
        val schema = form.path("schema")
        assertEquals(names.split(", "), schema.path("properties").fieldNames().asSequence().toList())
        assertEquals(required.split(", "), schema.path("required").map { it.asText() })
    }

    @Then("the index form {string} field {string} is an integer from {int}")
    fun integer(key: String, field: String, minimum: Int) {
        val property = property(key, field)
        assertEquals("integer", property.path("type").asText())
        assertEquals(minimum, property.path("minimum").asInt())
    }

    @Then("the index form {string} field {string} is one of {string}")
    fun oneOf(key: String, field: String, values: String) {
        assertEquals(values.split(", "), property(key, field).path("enum").map { it.asText() })
    }

    @Given("the search of {string} for {string} finds {string} and {string}")
    fun finds(repository: String, state: String, first: String, second: String) {
        val (owner, repo) = repository.split("/")
        this.repository = owner to repo
        this.state = state
        found = listOf(first, second).map { match ->
            val (number, title) = match.split(" ", limit = 2)
            Found(number.toLong(), title, mapOf("owner" to owner, "repo" to repo, "number" to number))
        }
    }

    @When("page {int} of the search is rendered, {int} to a page")
    fun page(page: Int, size: Int) {
        val resource = HeartResource(
            layers = Layers(type),
            loadAt = { error("not read") },
            perform = { pull, _, _ -> Acted(pull) },
            search = { values ->
                check(values == mapOf("owner" to repository.first, "repo" to repository.second, "state" to state))
                found
            },
        )
        val variables = mapOf("owner" to repository.first, "repo" to repository.second)
        val (search, matches) = runBlocking { resource.searched(variables) { if (it == "state") state else null } }
        read = mapper.valueToTree(SearchPages.page(search, matches, page, size))
    }

    @Then("the page's self link is {string}")
    fun pageSelf(href: String) = pageLink("self", href)

    @Then("the page's {string} link is {string}")
    fun pageLink(rel: String, href: String) {
        assertEquals(href, read.path("_links").path(rel).path("href").asText())
    }

    @Then("the page's items are {string}")
    fun items(expected: String) {
        val items = read.path("_links").path("item").map { "${it.path("href").asText()} ${it.path("title").asText()}" }
        assertEquals(expected.split(", "), items)
    }

    @Then("the resource {string} has the template {string} with variables {string}")
    fun homeResource(name: String, template: String, variables: String) {
        val resource = read.path("resources").path(name)
        assertEquals(template, resource.path("hrefTemplate").asText())
        assertEquals(variables.split(", "), resource.path("hrefVars").fieldNames().asSequence().toList())
    }

    @When("the OpenAPI description of the found pull request type is rendered")
    fun openApi() {
        val resource = HeartResource(
            layers = Layers(type),
            loadAt = { error("not read") },
            perform = { pull, _, _ -> Acted(pull) },
            search = { emptyList() },
        )
        read = mapper.readTree(OpenApiDocument(listOf(resource)).body)
    }

    @Then("its operation {string} is {string} with the parameters {string}")
    fun operation(operationId: String, route: String, parameters: String) {
        val (method, path) = route.split(" ")
        val operation = read.path("paths").path(path).path(method.lowercase())
        assertEquals(operationId, operation.path("operationId").asText())
        assertEquals(parameters.split(", "), operation.path("parameters").map { it.path("name").asText() })
    }

    @Then("it has no template {string} or {string}")
    fun noTemplates(first: String, second: String) {
        assertFalse(read.path("_templates").has(first), "templates: ${read.path("_templates")}")
        assertFalse(read.path("_templates").has(second), "templates: ${read.path("_templates")}")
    }

    @When("a pull request type is found at {string} with no field {string}")
    fun foundWithout(collectionPath: String, field: String) {
        val fields = PullRequests.FIELDS.filter { it.name != field }
        refusal = runCatching { PullRequests.type(finding = PullRequests.finding(collectionPath, fields)) }
            .exceptionOrNull()
    }

    @Then("the found type is refused, saying {string}")
    fun refused(message: String) {
        assertEquals(message, refusal?.message)
    }

    private fun property(key: String, field: String): JsonNode =
        read.path("_forms").path(key).path("schema").path("properties").path(field)
}
