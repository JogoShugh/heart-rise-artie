package com.artie.heart.spring.bdd

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult

/** The served OpenAPI description, and what links to it. */
class OpenApiSteps(private val http: HttpWorld) {
    private val mapper = ObjectMapper()

    @Then("the response has a Link to {string} with rel {string}")
    fun linkHeader(href: String, rel: String) {
        val links = http.last.responseHeaders[HttpHeaders.LINK].orEmpty().joinToString(", ")
        assertTrue("<$href>; rel=\"$rel\"" in links, links)
    }

    @Then("the home document's api links {string} to {string}")
    fun homeLinks(rel: String, href: String) =
        assertEquals(href, json(http.last).path("api").path("links").path(rel).asText())

    @Then("the description's operations are {string}")
    fun operations(expected: String) {
        val operations = json(http.last).path("paths").fields().asSequence().flatMap { (path, item) ->
            item.fieldNames().asSequence().filter { it in METHODS }.map { "${it.uppercase()} $path" }
        }.toList()
        assertEquals(expected.split(",").map { it.trim() }.toSet(), operations.toSet())
    }

    @Then("the plain response satisfies the description's schema {string}")
    fun satisfies(name: String) {
        val description = json(http.results.last { it.url.path == "/openapi.json" })
        val schema = description.path("components").path("schemas").path(name)
        assertTrue(schema.isObject, "no schema $name")
        val failures = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema)
            .validate(json(http.last))
        assertTrue(failures.isEmpty(), "$failures")
    }

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode = mapper.readTree(result.responseBody)

    private companion object {
        val METHODS = setOf("get", "put", "post", "delete", "patch")
    }
}
