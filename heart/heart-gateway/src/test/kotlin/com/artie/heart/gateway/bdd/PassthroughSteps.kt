package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** GitHub reads the connector lists but doesn't model, passed through the gateway (spec/PLAN-GATEWAY-URL-LINKS.md). */
class PassthroughSteps(private val github: GitHubSteps) {
    private val answer: JsonNode get() = github.lastAnswer

    @Given("{string} has these followers upstream")
    fun followers(login: String, table: DataTable) {
        github.upstream.followers[login] = table.asMaps().map { it.getValue("login") }
    }

    @When("{string} follows the answer's {string} link through the GitHub gateway")
    fun follows(caller: String, relation: String) {
        github.getAs(caller, answer.path("_links").path(relation).path("href").asText())
    }

    @Then("the answer offers no forms")
    fun noForms() = assertFalse(answer.has("_forms") || answer.has("_templates"), "$answer")

    @Then("the answer embeds these items")
    fun embedsItems(table: DataTable) {
        val items = answer.path("_embedded").path("item").map { item ->
            mapOf("self" to self(item), "login" to item.path("login").asText())
        }
        assertEquals(table.asMaps(), items, "$answer")
    }

    @Then("the answer lists one item, titled {string}, and embeds it")
    fun listsOne(title: String) {
        val items = answer.path("_links").path("item")
        assertEquals(listOf(title), items.map { it.path("title").asText() }, "$answer")
        val embedded = answer.path("_embedded").path("item")
        assertEquals(items.map { it.path("href").asText() }, embedded.map(::self))
    }

    private fun self(resource: JsonNode): String = resource.path("_links").path("self").path("href").asText()
}
