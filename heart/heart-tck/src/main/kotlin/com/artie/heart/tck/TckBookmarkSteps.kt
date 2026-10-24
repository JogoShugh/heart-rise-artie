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
        val target = world.targets().single { it == fixture.searchTarget }
        world.get(expand(target, fixture.searchValues))
    }

    @Then("the index offers the type's search and lookup forms")
    fun indexForms() {
        val targets = world.targets()
        assertTrue(fixture.searchTarget in targets, "index targets: $targets")
        assertTrue(fixture.lookupTarget in targets, "index targets: $targets")
    }

    @Then("the results list the resource")
    fun resultsList() {
        val items = world.json.path("_links").path("item").map { it.path("href").asText() }
        assertTrue(world.resource in items, "items: $items")
    }

    @Then("the home document names the resource's collection")
    fun homeNames() {
        val templates = world.json.path("resources").map { it.path("hrefTemplate").asText() }
        assertTrue(fixture.lookupTarget in templates, "templates: $templates")
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

    private companion object {
        val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
        val QUERY = Regex("""\{\?([A-Za-z0-9_,]+)}""")

        /** RFC 6570, as far as a search form uses it: `{name}` and `{?a,b}`, leaving out the unfilled. */
        fun expand(template: String, values: Map<String, String>): String {
            val query = QUERY.replace(template) { match ->
                val pairs = match.groupValues[1].split(",").mapNotNull { name ->
                    values[name]?.let { "$name=" + URLEncoder.encode(it, Charsets.UTF_8) }
                }
                if (pairs.isEmpty()) "" else pairs.joinToString("&", "?")
            }
            return VARIABLE.replace(query) { match ->
                URLEncoder.encode(checkNotNull(values[match.groupValues[1]]), Charsets.UTF_8)
            }
        }
    }
}
