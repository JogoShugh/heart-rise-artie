package com.artie.gardenbed.bdd

import com.artie.heart.ServingLimits
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals

/** Steps for SPEC §6.11 paging: next, prev, first and last links on a collection. */
class PagingSteps(private val http: HttpWorld, private val limits: ServingLimits) {

    @Given("the server lists {int} beds per page")
    fun pageSize(size: Int) {
        limits.pageSize = size
    }

    /** The page size is a singleton in the Spring context; every scenario starts at the default. */
    @After
    fun restorePageSize() {
        limits.pageSize = ServingLimits.DEFAULT_PAGE_SIZE
    }

    /** Follows a link by relation, with the headers the last request carried. The client builds no URI. */
    @When("the client follows the {string} link")
    fun follow(rel: String) {
        val href = http.last.json.path("_links").path(rel).path("href").asText()
        http.get(href, http.last.requestHeaders)
    }

    /** "nothing", or a list like `"/beds/1" titled "North border"`; [count] is checked separately. */
    @Then("the page lists the items {}")
    fun pageLists(expected: String) {
        val wanted = ITEM.findAll(expected).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val actual = http.last.json.path("_links").path("item")
            .map { it.path("href").asText() to it.path("title").asText() }
        assertEquals(wanted, actual, "items in ${http.last.json}")
    }

    @Then("the response counts {int} matches")
    fun counts(count: Int) = assertEquals(count, http.last.json.path("count").asInt(-1))

    @Then("the response links {word} to {string}")
    fun linksTo(rel: String, href: String) =
        assertEquals(href, http.last.json.path("_links").path(rel).path("href").asText(), "$rel in ${http.last.json}")

    private companion object {
        val ITEM = Regex(""""([^"]+)" titled "([^"]+)"""")
    }
}
