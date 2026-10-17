package com.artie.heart.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.Found
import com.artie.heart.Rung
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.security.MessageDigest

class IndexSteps(private val world: LampWorld) {
    private val mapper = ObjectMapper()
    private var found: List<Found> = emptyList()
    private var json: JsonNode = mapper.createObjectNode()
    private val index get() = ServiceIndex(listOf(world.type))

    @Given("lamps {int} to {int} exist")
    fun lampsExist(from: Int, to: Int) {
        found = (from..to).map { Found(it.toLong(), "Lamp $it") }
    }

    @When("the service index is rendered in HAL Schema Forms")
    fun indexSchemaForms() {
        json = mapper.readTree(index.body(Dialect.JSONSCHEMA))
    }

    @When("the service index is rendered in HAL-FORMS")
    fun indexHalForms() {
        json = mapper.readTree(index.body(Dialect.HALFORMS))
    }

    @Then("the index is titled {string} and links {string} to {string}")
    fun titled(title: String, rel: String, href: String) {
        assertEquals(title, json.path("title").asText())
        assertEquals(href, json.path("_links").path(rel).path("href").asText())
    }

    @Then("it offers these GET forms")
    fun getForms(table: DataTable) = table.asMaps().forEach {
        getForm(it.getValue("form"), it.getValue("target"), it.getValue("required field"))
    }

    private fun getForm(key: String, target: String, field: String) {
        val form = json.path("_forms").path(key)
        assertEquals("GET", form.path("method").asText())
        assertEquals(target, form.path("_links").path("target").path("href").asText())
        assertEquals(true, form.path("_links").path("target").path("templated").asBoolean())
        assertEquals(listOf(field), form.path("schema").path("required").map { it.asText() })
        assertFalse(form.path("schema").path("properties").path(field).isMissingNode)
    }

    @Then("it offers these GET templates")
    fun getTemplates(table: DataTable) = table.asMaps().forEach {
        getTemplate(it.getValue("template"), it.getValue("target"), it.getValue("required property"))
    }

    private fun getTemplate(name: String, target: String, property: String) {
        val template = json.path("_templates").path(name)
        assertEquals("GET", template.path("method").asText())
        assertEquals(target, template.path("target").asText())
        val declared = template.path("properties").single()
        assertEquals(property to true, declared.path("name").asText() to declared.path("required").asBoolean())
    }

    @Then(
        "the index's validator in each dialect is {string} with the dialect's tag " +
            "and the first 16 hex digits of its body's digest",
    )
    fun validator(prefix: String) = Dialect.entries.forEach { dialect ->
        val hex = MessageDigest.getInstance("SHA-256").digest(index.body(dialect).toByteArray())
            .joinToString("") { "%02x".format(it) }.take(HEX)
        assertEquals("\"$prefix${dialect.etagTag}-$hex\"", index.validator(dialect))
    }

    @Then("it offers these POST forms")
    fun postForms(table: DataTable) = table.asMaps().forEach {
        postForm(it.getValue("form"), it.getValue("target"), it.getValue("required field"))
    }

    private fun postForm(key: String, target: String, field: String) {
        val form = json.path("_forms").path(key)
        assertEquals("POST", form.path("method").asText())
        assertEquals("application/json", form.path("contentType").asText())
        assertEquals(target, form.path("_links").path("target").path("href").asText())
        assertFalse(form.path("_links").path("target").path("templated").asBoolean())
        assertEquals(listOf(field), form.path("schema").path("required").map { it.asText() })
        assertTrue(form.path("schema").path("\$id").asText().matches(Regex("/schemas/[0-9a-f]{16}")))
    }

    @Then("it offers these POST templates")
    fun postTemplates(table: DataTable) = table.asMaps().forEach {
        postTemplate(
            it.getValue("template"), it.getValue("target"), it.getValue("required property"),
            it.getValue("min").toInt(), it.getValue("max").toInt(),
        )
    }

    private fun postTemplate(name: String, target: String, property: String, min: Int, max: Int) {
        val template = json.path("_templates").path(name)
        assertEquals("POST", template.path("method").asText())
        assertEquals("application/json", template.path("contentType").asText())
        assertEquals(target, template.path("target").asText())
        val declared = template.path("properties").single { it.path("name").asText() == property }
        assertEquals(true, declared.path("required").asBoolean())
        assertEquals(min to max, declared.path("min").asInt() to declared.path("max").asInt())
    }

    @Then("the schema of {string} is served at its content address")
    fun actSchema(name: String) {
        val schema = ContentAddress.canonicalText(world.type.act(name).schema)
        assertEquals(schema, world.layers.schemas[ContentAddress.address(schema)])
    }

    @When("the home document is rendered for a server capped at {word}")
    fun home(max: String) {
        json = mapper.readTree(index.home(Rung.valueOf(max)))
    }

    @Then("it names these resources at their templates")
    fun names(table: DataTable) = table.asMaps().forEach { row ->
        val resource = json.path("resources").path(row.getValue("resource"))
        assertEquals(row.getValue("template"), resource.path("hrefTemplate").asText())
    }

    @Then("the {string} hints offer both dialects with their profiles, and need an ETag to act")
    fun hints(item: String) {
        val hints = json.path("resources").path(item).path("hints")
        Dialect.entries.forEach {
            assertEquals(it.profile, hints.path("formats").path(it.mediaType).path("links").path("profile").asText())
        }
        assertEquals(listOf("etag"), hints.path("precondition-req").map { it.asText() })
    }

    @Then("the {string} hint {string} is {}")
    fun hint(item: String, name: String, value: String) {
        val hint = json.path("resources").path(item).path("hints").path(name)
        if (value == "absent") {
            assertTrue(hint.isMissingNode, "$name is $hint")
        } else {
            assertEquals(value.removeSurrounding("\"").split(", "), hint.map { it.asText() })
        }
    }

    @When("page {int} of the lamps matching {string} is rendered, {int} to a page")
    fun page(page: Int, name: String, size: Int) {
        val search = Search(world.type.collection, name.ifEmpty { null })
        json = mapper.valueToTree(SearchPages.page(search, found, page, size))
    }

    @Then("each page of the lamps matching {string}, {int} to a page, counts {int} and lists")
    fun pagesList(name: String, size: Int, count: Int, table: DataTable) = byPage(table, name, size) { rows ->
        assertEquals(count, json.path("count").asInt())
        val listed = json.path("_links").path("item")
            .map { mapOf("href" to it.path("href").asText(), "title" to it.path("title").asText()) }
        assertEquals(rows.map { mapOf("href" to it["href"], "title" to it["title"]) }, listed)
    }

    @Then("each page of the lamps matching {string}, {int} to a page, links")
    fun pagesLink(name: String, size: Int, table: DataTable) = byPage(table, name, size) { rows ->
        val actual = listOf("first", "last", "next", "prev")
            .filter { json.path("_links").has(it) }
            .associateWith { json.path("_links").path(it).path("href").asText() }
        assertEquals(rows.associate { it.getValue("rel") to it.getValue("href") }, actual)
    }

    /** Each page's rows of [table], checked by [check] against that page of the search, [size] to a page. */
    private fun byPage(table: DataTable, name: String, size: Int, check: (List<Map<String, String>>) -> Unit) =
        table.asMaps().groupBy { it.getValue("page").toInt() }.forEach { (page, rows) ->
            page(page, name, size)
            check(rows)
        }

    private companion object {
        const val HEX = 16
    }
}
