package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.fixtures.Branch
import com.artie.heart.gateway.fixtures.Committed
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals

/** Branches, committed files and new pull requests on the small GitHub (github-branches.feature). */
class BranchSteps(private val github: GitHubSteps) {

    @Given("{string} has these branches upstream")
    fun branches(repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        github.upstream.branches += table.asMaps().map {
            Branch(owner, repo, it.getValue("name"), it.getValue("sha"), it.getValue("protected").toBoolean())
        }
    }

    @Then("the {string} form's field {string} is described as {string}")
    fun described(key: String, field: String, description: String) {
        val property = github.lastAnswer.path("_forms").path(key).path("schema").path("properties").path(field)
        assertEquals(description, property.path("description").asText(), "$property")
    }

    @Then("the forms title their fields")
    fun titled(table: DataTable) {
        table.asMaps().forEach { row ->
            val act = row.getValue("act")
            val field = row.getValue("field")
            val property = github.lastAnswer.path("_forms").path(act).path("schema").path("properties").path(field)
            assertEquals(row.getValue("title"), property.path("title").asText(), "$act $field: $property")
            row["description"]?.let { assertEquals(it, property.path("description").asText(), "$act $field") }
        }
    }

    @Then("GitHub holds the file {string} on {string} of {string}, saying {string}, committed as {string}")
    fun holds(path: String, branch: String, repository: String, text: String, message: String) {
        val (owner, repo) = repository.split("/")
        // A Gherkin string keeps \n as written; the file holds a newline.
        val expected = Committed(owner, repo, branch, path, text.replace("\\n", "\n"), message)
        assertEquals(listOf(expected), github.upstream.committed)
    }
}
