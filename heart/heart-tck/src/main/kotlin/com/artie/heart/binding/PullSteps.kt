package com.artie.heart.binding

import com.artie.heart.fixtures.PullRequest
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals

/** The pull requests in the server under test, set up and checked by owner, repository and number. */
class PullSteps(private val store: PullStore) {

    /** Each scenario starts with no pull requests: one a scenario splits off doesn't linger in the next. */
    @Before
    fun empty() {
        store.pulls.clear()
    }

    @Given("pull request {long} of {string} is {string} in the pull request server")
    fun pullIs(number: Long, repository: String, state: String) {
        val (owner, repo) = repository.split("/")
        store.put(PullRequest(owner, repo, number, state, version = 1))
    }

    @Given("pull request {long} of {string} is {string} in the pull request server, written by {string}")
    fun pullIsBy(number: Long, repository: String, state: String, login: String) {
        val (owner, repo) = repository.split("/")
        store.put(PullRequest(owner, repo, number, state, version = 1, author = mapOf("login" to login)))
    }

    @When("the author of pull request {long} of {string} becomes {string} in the pull request server")
    fun authorBecomes(number: Long, repository: String, login: String) {
        val (owner, repo) = repository.split("/")
        store.pulls.computeIfPresent(Triple(owner, repo, number)) { _, pull ->
            pull.copy(author = mapOf("login" to login))
        }
    }

    @Then("the pull request server was last asked by {string}")
    fun askedBy(authorization: String) {
        assertEquals(authorization, store.lastCaller)
    }

    @Then("pull request {long} of {string} is now {string} in the pull request server")
    fun pullIsNow(number: Long, repository: String, state: String) {
        val (owner, repo) = repository.split("/")
        assertEquals(state, store.pulls.getValue(Triple(owner, repo, number)).state)
    }
}
