package com.artie.heart.binding

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** The forms, templates, links and profiles a response carries. */
class FormSteps(private val http: HttpWorld) {

    @Then("the response declares the CURIE {string} as {string}")
    fun curie(name: String, href: String) {
        val curies = http.json.path("_links").path("curies")
        val declared = curies.any {
            it.path("name").asText() == name && it.path("href").asText() == href && it.path("templated").asBoolean()
        }
        assertTrue(declared, "no CURIE $name -> $href in $curies")
    }

    @Then("the {string} form has a title and a description")
    fun described(key: String) {
        val form = http.json.path("_forms").path(key)
        assertTrue(form.path("title").asText().isNotBlank(), "no title on $key: $form")
        assertTrue(form.path("description").asText().isNotBlank(), "no description on $key: $form")
    }

    @Then("the response has no member {string}")
    fun hasNo(path: String) {
        val node = path.split('.').fold(http.json) { at, key -> at.path(key) }
        assertTrue(node.isMissingNode, "$path present: $node")
    }

    @Then("the template targeting {string} has the property {string}, required, from {int} to {int}")
    fun templateProperty(target: String, name: String, min: Int, max: Int) {
        val template = http.json.path("_templates").first { it.path("target").asText() == target }
        val property = template.path("properties").first { it.path("name").asText() == name }
        val actual = listOf("required", "min", "max").map { property.path(it).asText() }
        assertEquals(listOf("true", min.toString(), max.toString()), actual, "$property")
    }

    @Then("no property of any template is read-only")
    fun noReadOnly() {
        val properties = http.json.path("_templates").flatMap { it.path("properties") }
        val readOnly = properties.filter { it.path("readOnly").asBoolean() }
        assertTrue(readOnly.isEmpty(), "read-only: $readOnly")
    }

    @Then("the ALPS document describes {string} as {string}")
    fun alps(id: String, type: String) {
        val descriptor = http.json.path("alps").path("descriptor").firstOrNull { it.path("id").asText() == id }
        assertEquals(type, checkNotNull(descriptor) { "no descriptor $id" }.path("type").asText())
    }

    @Then("the response links {string} to {string}")
    fun linksTo(rel: String, href: String) =
        assertEquals(href, http.json.path("_links").path(rel).path("href").asText())

    @Then("the response has no link under {string}")
    fun noLink(rel: String) =
        assertFalse(http.json.path("_links").has(rel), "found $rel in ${http.json.path("_links")}")

    @Then("the response lists these items")
    fun items(table: DataTable) {
        val listed = http.json.path("_links").path("item")
            .map { mapOf("href" to it.path("href").asText(), "title" to it.path("title").asText()) }
        assertEquals(table.asMaps(), listed)
    }

    /** A HAL-FORMS target cannot be templated, so a search or lookup that fills a path has no template. */
    @Then("the two indexes offer forms with the same titles, but for these")
    fun sameTitles(table: DataTable) {
        val untemplated = table.asMaps().map { it.getValue("title") }.toSet()
        val (hsf, hf) = http.results.takeLast(2).map(http::json)
        val forms = hsf.path("_forms").map { it.path("title").asText() }.toSet()
        assertTrue(forms.isNotEmpty(), "no forms in $hsf")
        val templates = hf.path("_templates").map { it.path("title").asText() }.toSet()
        assertEquals(forms - untemplated, templates)
    }
}
