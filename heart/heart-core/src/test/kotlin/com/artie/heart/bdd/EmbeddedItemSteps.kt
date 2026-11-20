package com.artie.heart.bdd

import com.artie.heart.Found
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** A search page that embeds a summary of each match (SPEC §6.11). */
class EmbeddedItemSteps(private val world: LampWorld) {
    private val mapper = ObjectMapper()
    private var found: List<Found> = emptyList()
    private lateinit var page: JsonNode

    @Given("these lamps are found, with summaries")
    fun foundWithSummaries(table: DataTable) {
        found = table.asMaps().map {
            Found(it.getValue("id").toLong(), it.getValue("title"), summary = mapOf("status" to it.getValue("status")))
        }
    }

    @Given("these lamps are found, with no summary")
    fun foundWithout(table: DataTable) {
        found = table.asMaps().map { Found(it.getValue("id").toLong(), it.getValue("title")) }
    }

    @When("page {int} of the matches is rendered, {int} to a page")
    fun rendered(number: Int, size: Int) {
        page = mapper.valueToTree(SearchPages.page(Search(world.type.collection), found, number, size))
    }

    @When("page {int} of the matches is rendered in plain JSON, {int} to a page")
    fun renderedPlain(number: Int, size: Int) {
        page = mapper.valueToTree(SearchPages.plainPage(found, number, size))
    }

    @Then("the page embeds these items")
    fun embedsThese(table: DataTable) {
        val embedded = page.path("_embedded").path("item").map {
            mapOf(
                "self" to it.path("_links").path("self").path("href").asText(),
                "title" to it.path("_links").path("self").path("title").asText(),
                "status" to it.path("status").asText(),
            )
        }
        assertEquals(table.asMaps(), embedded)
    }

    @Then("the page links these items")
    fun linksThese(table: DataTable) {
        val links = page.path("_links").path("item").map {
            mapOf("href" to it.path("href").asText(), "title" to it.path("title").asText())
        }
        assertEquals(table.asMaps(), links)
    }

    @Then("no embedded item carries forms")
    fun noForms() = page.path("_embedded").path("item").forEach {
        assertFalse(it.has("_forms") || it.has("_templates"), "$it")
    }

    @Then("the page embeds nothing")
    fun embedsNothing() = assertFalse(page.has("_embedded"), "$page")

    @Then("the plain page is exactly {}")
    fun plainExactly(json: String) = assertEquals(json, mapper.writeValueAsString(page))
}
