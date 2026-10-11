package com.artie.heart.tck

import com.artie.heart.Dialect
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import java.net.URLEncoder

/** The bookmark, the search, both dialects, and the profile behind the forms. */
class TckBookmarkSteps(private val fixture: TckFixture, private val world: TckWorld) {

    @When("a client gets {string} as hal-schema-forms")
    fun getPath(path: String) {
        world.get(path, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType))
    }

    @When("a client gets {string} with Accept {string}")
    fun getWithAccept(path: String, accept: String) {
        world.get(path, mapOf(HttpHeaders.ACCEPT to accept))
    }

    @When("a client follows the search form with the fixture's search term")
    fun search() {
        val target = world.targets().single { it == "/${fixture.collection}{?name}" }
        world.get(target.replace("{?name}", "?name=" + URLEncoder.encode(fixture.searchTerm, Charsets.UTF_8)))
    }

    @Then("the index offers the type's search and lookup forms")
    fun indexForms() {
        val targets = world.targets()
        assertTrue("/${fixture.collection}{?name}" in targets, "index targets: $targets")
        assertTrue("/${fixture.collection}/{id}" in targets, "index targets: $targets")
    }

    @Then("the results list the resource")
    fun resultsList() {
        val items = world.json.path("_links").path("item").map { it.path("href").asText() }
        assertTrue(world.resource in items, "items: $items")
    }

    @Then("the home document names the resource's collection")
    fun homeNames() {
        val templates = world.json.path("resources").map { it.path("hrefTemplate").asText() }
        assertTrue("/${fixture.collection}/{id}" in templates, "templates: $templates")
    }

    @Then("a hal-schema-forms client and a hal-forms client see the same act targets on the resource")
    fun sameTargets() {
        world.get(world.resource, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType))
        val schemaForms = world.targets().toSet()
        world.get(world.resource, mapOf(HttpHeaders.ACCEPT to Dialect.HALFORMS.mediaType))
        val halForms = world.json.path("_templates").map { it.path("target").asText() }.toSet()
        assertEquals(schemaForms, halForms)
    }

    @Then("every relation its forms use is described in the server's ALPS profile")
    fun described() {
        val body = world.json
        val curies = body.path("_links").path("curies")
            .associate { it.path("name").asText() to it.path("href").asText() }
        world.forms(body).keys.forEach { key ->
            val href = checkNotNull(curies[key.substringBefore(':')]) { "no CURIE for $key" }
            val (path, rel) = href.replace("{rel}", key.substringAfter(':')).split('#')
            world.get(path)
            val ids = world.json.path("alps").path("descriptor").map { it.path("id").asText() }
            assertTrue(rel in ids, "$rel is not in $path: $ids")
        }
    }
}
