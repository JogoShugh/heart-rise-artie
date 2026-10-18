package com.artie.heart.bdd

import com.artie.heart.Embedded
import com.artie.heart.ItemPath
import com.artie.heart.fixtures.PullRequest
import com.artie.heart.fixtures.PullRequests
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** A pull request type that embeds its author and its reviewers (SPEC §6.1, `_embedded`). */
class EmbeddedSteps(private val world: LampWorld, private val pull: PullWorld) {
    private val last: JsonNode get() = pull.rendered.last()

    @Given("the pull request type embeds these resources")
    fun embeds(table: DataTable) {
        pull.embedded = embeddings(table)
        pull.declare()
    }

    @Given("the author embeds this resource")
    fun authorEmbeds(table: DataTable) {
        pull.embedded = pull.embedded.map {
            if (it.relation == "author") Embedded(it.relation, it.pointer, it.self, embeddings(table)) else it
        }
        pull.declare()
    }

    @Given("pull request {long} of {string} is open, written by {string}")
    fun openBy(number: Long, repository: String, login: String) {
        val (owner, repo) = repository.split("/")
        pull.pulls += PullRequest(owner, repo, number, "open", version = 1, author = mapOf("login" to login))
    }

    @Given("the pull request's state says these links")
    fun stateLinks(table: DataTable) {
        pull.stateLinks = table.asMaps().associate {
            it.getValue("relation") to mapOf("href" to it.getValue("href"), "type" to it.getValue("type"))
        }
        pull.declare()
    }

    @Given("the pull request's state embeds its merger {string} at {string}")
    fun stateEmbeds(login: String, self: String) {
        val merger = mapOf("login" to login, "_links" to mapOf("self" to mapOf("href" to self)))
        pull.stateEmbedded = mapOf("pulls:merger" to merger)
        pull.declare()
    }

    @Then("it links these resources by their types")
    fun linksByType(table: DataTable) {
        val links = last.path("_links")
        table.asMaps().forEach { row ->
            val link = links.path(row.getValue("relation"))
            assertEquals(row.getValue("href"), link.path("href").asText(), "$links")
            assertEquals(row.getValue("type"), link.path("type").asText(), "$links")
        }
    }

    @Given("its reviewers are")
    fun reviewers(table: DataTable) {
        update { it.copy(reviewers = table.asMaps().map { row -> row.getValue("login") }) }
    }

    @Given("the author belongs to the organization {string}")
    fun organization(name: String) {
        update { it.copy(author = it.author.orEmpty() + ("org" to mapOf("name" to name))) }
    }

    @Given("the pull request has no author")
    fun noAuthor() {
        update { it.copy(author = null) }
    }

    @Given("the pull request has a null author")
    fun nullAuthor() {
        update { it.copy(author = null, nullAuthor = true) }
    }

    @When("a pull request type is declared embedding {string} at {string} with the self {string}")
    fun declaredEmbedding(relation: String, pointer: String, self: String) {
        pull.refusal = runCatching {
            val embedded = Embedded(relation, pointer, ItemPath(self, mapOf("login" to "/login")))
            PullRequests.type(ItemPath(pull.template, PullRequests.POINTERS), embedded = listOf(embedded))
        }.exceptionOrNull()
    }

    @Then("it embeds these resources")
    fun embedsThese(table: DataTable) {
        assertEmbeds(last.path("_embedded"), table)
    }

    @Then("the embedded author embeds these resources")
    fun authorEmbedsThese(table: DataTable) {
        assertEmbeds(last.path("_embedded").path("pulls:author").path("_embedded"), table)
    }

    @Then("these members are not in its state")
    fun notInState(table: DataTable) {
        table.asMaps().forEach { assertFalse(last.has(it.getValue("member")), "${it.getValue("member")} in $last") }
    }

    @Then("the embedded author has no member {string}")
    fun authorHasNo(member: String) {
        val author = last.path("_embedded").path("pulls:author")
        assertFalse(author.has(member), "$member in $author")
    }

    @Then("no embedded resource carries forms")
    fun noForms() {
        resources(last.path("_embedded")).forEach {
            assertFalse(it.has("_forms") || it.has("_templates") || it.path("_links").has("hr:forms"), "$it")
        }
    }

    @Then("it embeds no {string}")
    fun embedsNo(relation: String) {
        assertFalse(last.path("_embedded").has(relation), "$last")
    }

    @Then("the lamp type embeds nothing")
    fun lampEmbedsNothing() {
        assertEquals(emptyList<Embedded>(), world.type.embedded)
    }

    private fun update(change: (PullRequest) -> PullRequest) {
        pull.pulls[0] = change(pull.pulls[0])
    }

    private fun embeddings(table: DataTable): List<Embedded> = table.asMaps().map {
        val self = ItemPath(it.getValue("self"), mapOf(it.getValue("variable") to it.getValue("from")))
        Embedded(it.getValue("relation"), it.getValue("pointer"), self)
    }

    private fun assertEmbeds(embedded: JsonNode, table: DataTable) {
        table.asMaps().forEach { row ->
            val at = embedded.path(row.getValue("relation"))
            val resource = row["index"]?.takeIf { it.isNotEmpty() }?.let { at.path(it.toInt()) } ?: at
            assertTrue(resource.isObject, "no ${row.getValue("relation")} ${row["index"].orEmpty()} in $embedded")
            assertEquals(row.getValue("self"), resource.path("_links").path("self").path("href").asText(), "$resource")
            row.filterKeys { it !in setOf("relation", "index", "self") }.forEach { (member, value) ->
                assertEquals(value, resource.path(member).asText(), "$member of $resource")
            }
        }
    }

    private fun resources(embedded: JsonNode): List<JsonNode> =
        embedded.properties().flatMap { (_, value) -> if (value.isArray) value.toList() else listOf(value) }
            .flatMap { listOf(it) + resources(it.path("_embedded")) }
}
