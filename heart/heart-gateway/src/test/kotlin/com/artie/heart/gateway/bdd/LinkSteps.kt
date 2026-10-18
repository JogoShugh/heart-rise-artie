package com.artie.heart.gateway.bdd

import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** GitHub's link members as HEART links and embedded resources (spec/PLAN-GATEWAY-URL-LINKS.md). */
class LinkSteps(private val github: GitHubSteps) {
    private val answer: JsonNode get() = github.lastAnswer

    @Then("the answer links these, by type")
    fun linksByType(table: DataTable) = assertLinks(answer.path("_links"), table)

    @Then("the answer's state has none of these members")
    fun noneOf(table: DataTable) = table.asMaps().forEach {
        assertFalse(answer.has(it.getValue("member")), "${it.getValue("member")} is in the state")
    }

    @Then("the answer's state still has these members")
    fun stillHas(table: DataTable) = table.asMaps().forEach {
        assertTrue(answer.has(it.getValue("member")), "${it.getValue("member")} is not in the state: $answer")
    }

    @Then("the answer embeds {string} with its self at {string} and these links")
    fun embeds(relation: String, self: String, table: DataTable) {
        val embedded = answer.path("_embedded").path(relation)
        assertTrue(embedded.isObject, "no $relation in ${answer.path("_embedded")}")
        assertEquals(self, embedded.path("_links").path("self").path("href").asText(), "$embedded")
        assertFalse(embedded.has("_forms") || embedded.has("_templates"), "forms in $embedded")
        assertLinks(embedded.path("_links"), table)
    }

    @Then("the embedded {string} still has the member {string} as {string}")
    fun embeddedHas(relation: String, member: String, value: String) =
        assertEquals(value, answer.path("_embedded").path(relation).path(member).asText())

    private fun assertLinks(links: JsonNode, table: DataTable) = table.asMaps().forEach { row ->
        val relation = row.getValue("relation")
        val link = links.path(relation)
        assertEquals(row.getValue("href"), link.path("href").asText(), "$relation in $links")
        row["type"]?.takeIf { it.isNotEmpty() }?.let {
            assertEquals(it, link.path("type").asText(), "$relation: $link")
        }
        val templated = row["templated"]?.takeIf { it.isNotEmpty() }?.toBoolean() ?: false
        assertEquals(templated, link.path("templated").asBoolean(false), "$relation: $link")
    }
}
