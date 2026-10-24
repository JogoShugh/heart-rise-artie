package com.artie.heart.binding

import com.artie.heart.ContentAddress
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/** The served OpenAPI description, and what links to it. */
class OpenApiSteps(private val http: HttpWorld, private val client: WebTestClient) {
    private val mapper = ObjectMapper()
    private var set: String = ""
    private var integrity: String = ""

    @Then("the response's only Link is the forms link to an affordance set")
    fun onlyFormsLink() {
        val links = http.last.responseHeaders[HttpHeaders.LINK].orEmpty()
        assertEquals(1, links.size, "$links")
        assertTrue(links.single().startsWith("<$AFFORDANCE_SETS") && "rel=\"$FORMS_REL\"" in links.single(), "$links")
    }

    @Then("the plain response's forms link names an affordance set that offers")
    fun plainFormsLink(table: DataTable) {
        val link = http.last.responseHeaders[HttpHeaders.LINK].orEmpty().single { "rel=\"$FORMS_REL\"" in it }
        val href = link.substringAfter('<').substringBefore('>')
        integrity = Regex("""integrity="([^"]+)"""").find(link)?.groupValues?.get(1).orEmpty()
        val served = client.get().uri(href).exchange().expectStatus().isOk.expectBody().returnResult()
        set = String(checkNotNull(served.responseBody))
        val offered = mapper.readTree(set).path("_forms").fieldNames().asSequence().toSet()
        assertEquals(table.asMaps().map { it.getValue("form") }.toSet(), offered)
    }

    @Then("that affordance set's bytes match the link's integrity")
    fun setIntegrity() = assertEquals(ContentAddress.integrity(set), integrity)

    @Then("the response has a Link to {string} with rel {string}")
    fun linkHeader(href: String, rel: String) {
        val links = http.last.responseHeaders[HttpHeaders.LINK].orEmpty().joinToString(", ")
        assertTrue("<$href>; rel=\"$rel\"" in links, links)
    }

    @Then("the home document's api links {string} to {string}")
    fun homeLinks(rel: String, href: String) =
        assertEquals(href, json(http.last).path("api").path("links").path(rel).asText())

    @Then("the description's operations are")
    fun operations(table: DataTable) {
        val operations = json(http.last).path("paths").fields().asSequence().flatMap { (path, item) ->
            item.fieldNames().asSequence().filter { it in METHODS }.map { "${it.uppercase()} $path" }
        }.toList()
        val expected = table.asMaps().map { row -> "${row.getValue("method")} ${row.getValue("path")}" }
            .toSet()
        assertEquals(expected, operations.toSet())
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
        const val AFFORDANCE_SETS = "/affordance-sets/"
        const val FORMS_REL = "https://w3id.org/heart-rise/rels/forms"
        val METHODS = setOf("get", "put", "post", "delete", "patch")
    }
}
