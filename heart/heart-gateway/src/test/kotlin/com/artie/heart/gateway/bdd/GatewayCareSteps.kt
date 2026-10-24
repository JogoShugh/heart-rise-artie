package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.GatewayCare
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** github-care.feature: the care the gateway takes, turned off by a trial, by its environment or by a request. */
class GatewayCareSteps(private val github: GitHubSteps) {
    /** How many of GitHub's requests came before the ones a scenario counts. */
    private var counted = 0

    @Given("the GitHub gateway takes care as {string}, by its environment")
    fun standing(care: String) {
        github.standingCare = if (care == EVERY) GatewayCare.EVERY else GatewayCare.parse(care)
    }

    @Given("the GitHub gateway takes care as {string}, by the request")
    fun asked(care: String) {
        github.careAsked = care
    }

    @When("GitHub's requests are counted from here")
    fun countFromHere() {
        counted = github.upstream.requests.size
    }

    @Then("GitHub was asked")
    fun asked(table: DataTable) {
        val requests = github.upstream.requests.drop(counted).map { "${it.method} ${it.uri.path}" }
        table.asMaps().forEach { row ->
            val request = row.getValue("request")
            assertEquals(row.getValue("times").toInt(), requests.count { it == request }, "$request in $requests")
        }
    }

    @Then("the answer says {word} is {string}")
    fun member(name: String, value: String) =
        assertEquals(value, github.lastAnswer.path(name).asText(), "${github.lastAnswer}")

    @Then("the form {string} is shown")
    fun shown(key: String) = assertTrue(github.lastAnswer.path("_forms").has(key), "${github.lastAnswer}")

    private companion object {
        const val EVERY = "every kind"
    }
}
