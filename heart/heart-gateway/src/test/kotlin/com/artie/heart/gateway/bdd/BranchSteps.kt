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

    @Then("GitHub holds the file {string} on {string} of {string}, saying {string}, committed as {string}")
    fun holds(path: String, branch: String, repository: String, text: String, message: String) {
        val (owner, repo) = repository.split("/")
        // A Gherkin string keeps \n as written; the file holds a newline.
        val expected = Committed(owner, repo, branch, path, text.replace("\\n", "\n"), message)
        assertEquals(listOf(expected), github.upstream.committed)
    }
}
