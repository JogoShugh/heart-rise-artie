package com.artie.heart.tck

import com.artie.heart.Dialect
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpHeaders

/** What a caller with no credential is told, and what stays open to it. */
class TckCredentialSteps(private val fixture: TckFixture, private val world: TckWorld) {

    @When("a client with no credential gets the resource as hal-schema-forms")
    fun getResource() = bare { world.get(world.resource, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType)) }

    @When("a client with no credential gets {string} as hal-schema-forms")
    fun getPath(path: String) = bare { world.get(path, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType)) }

    @When("a client with no credential sends the offered act with its valid body")
    fun act() = bare {
        world.post("${world.resource}/actions/${fixture.offered.path}", fixture.offered.body, emptyMap())
    }

    @When("a client with no credential follows the {string} link")
    fun follow(rel: String) {
        val link = world.json.path("_links").path(rel)
        world.linkIntegrity = link.path("integrity").asText(null)
        bare { world.get(link.path("href").asText()) }
    }

    /** RFC 9110 §11.6.1: each challenge starts with its scheme; the realm is the server's to choose. */
    @Then("the response offers these challenges")
    fun challenges(table: DataTable) {
        val schemes = world.last.responseHeaders[HttpHeaders.WWW_AUTHENTICATE].orEmpty().map { it.substringBefore(' ') }
        assertEquals(table.asMaps().map { it.getValue("scheme") }, schemes)
    }

    private fun bare(request: () -> Unit) {
        world.credentialed = false
        try {
            request()
        } finally {
            world.credentialed = true
        }
    }
}
