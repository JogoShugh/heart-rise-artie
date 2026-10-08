package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for the service index (Fielding's Rule 6: one bookmark) and bed search. */
class IndexSteps(private val http: HttpWorld) {

    @When("a client gets the service index")
    fun getIndex() {
        http.get("/", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @When("a client gets {string}")
    fun getPath(path: String) {
        http.get(path, mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @When("a client gets the service index as {word}")
    fun getIndexAs(dialect: String) {
        http.get("/", mapOf("Accept" to HttpWorld.acceptFor(dialect)))
    }

    @When("a client gets {string} as {word}")
    fun getPathAs(path: String, dialect: String) {
        http.get(path, mapOf("Accept" to HttpWorld.acceptFor(dialect)))
    }

    @Then("the index offers HAL-FORMS GET template {string} with target {string} and required property {string}")
    fun offersHalFormsTemplate(name: String, target: String, property: String) {
        val template = http.last.json.path("_templates").path(name)
        assertEquals("GET", template.path("method").asText(), "method of $name in ${http.last.json}")
        assertEquals(target, template.path("target").asText())
        val found = template.path("properties").firstOrNull { it.path("name").asText() == property }
        assertTrue(found != null && found.path("required").asBoolean(), "$name has no required property $property")
    }

    @Then("the two indexes offer the same form names")
    fun sameFormNames() {
        val (hsf, hf) = http.exchanges.takeLast(2)
        val hsfNames = hsf.json.path("_forms").fieldNames().asSequence().toSet()
        val hfNames = hf.json.path("_templates").fieldNames().asSequence().toSet()
        assertTrue(hsfNames.isNotEmpty(), "no forms in ${hsf.json}")
        assertEquals(hsfNames, hfNames)
    }

    @Then("the response has an ETag")
    fun hasETag() = assertNotNull(http.last.headers.eTag, "no ETag")

    @Then("the index offers GET form {string} with target {string} and field {string}")
    fun offersGetForm(name: String, target: String, field: String) {
        val form = http.last.json.path("_forms").path(name)
        assertEquals("GET", form.path("method").asText(), "method of $name in ${http.last.json}")
        assertEquals(target, form.path("_links").path("target").path("href").asText())
        assertTrue(form.path("_links").path("target").path("templated").asBoolean(), "$name target is not templated")
        assertTrue(form.path("schema").path("properties").has(field), "$name has no field $field")
        assertEquals(field, form.path("schema").path("required").firstOrNull()?.asText(), "$field is not required")
    }

    /** "nothing", or a list like `"/beds/1" titled "North border", "/beds/2" titled "South border"`. */
    @Then("the response lists the items {}")
    fun listsItems(expected: String) {
        val wanted = ITEM.findAll(expected).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val items = http.last.json.path("_links").path("item")
        val actual = items.map { it.path("href").asText() to it.path("title").asText() }
        assertEquals(wanted, actual, "items in ${http.last.json}")
        assertEquals(wanted.size, http.last.json.path("count").asInt(-1), "count in ${http.last.json}")
    }

    @When("the client fills the {string} form with id {long} and follows it")
    fun fillAndFollow(name: String, id: Long) {
        val href = http.last.json.path("_forms").path(name).path("_links").path("target").path("href").asText()
        http.get(href.replace("{id}", id.toString()), mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @Then("the response is bed {long}")
    fun isBed(id: Long) {
        assertEquals(id, http.last.json.path("id").asLong(), "${http.last.json}")
        assertEquals("/beds/$id", http.last.json.path("_links").path("self").path("href").asText())
    }

    private companion object {
        val ITEM = Regex(""""([^"]+)" titled "([^"]+)"""")
    }
}
