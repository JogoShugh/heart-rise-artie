package com.artie.heart.binding

import com.artie.heart.ContentAddress
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

class ContentSteps(
    private val client: WebTestClient,
    @Qualifier(LampServer.BEAN) private val resource: HeartResource<*, *>,
    private val http: HttpWorld,
) {
    private val lamps get() = resource.layers
    private var results
        get() = http.results
        set(value) {
            http.results = value
        }
    private val last get() = http.last

    @Given("a server that declares only the lamp, with the profile {string}")
    fun aLampServer(name: String) = assertEquals(name, lamps.type.profile.name)

    @When("^a client gets every (affordance set|schema|explanation) of the lamp by its address$")
    fun getEvery(kind: String) {
        val (prefix, bodies) = when (kind) {
            "affordance set" -> Layers.AFFORDANCE_SETS to lamps.affordanceSets
            "schema" -> Layers.SCHEMAS to lamps.schemas
            else -> Layers.EXPLANATIONS to lamps.explanations
        }
        results = bodies.keys.map { get(prefix + it) }
    }

    @When("a client gets {string}")
    fun getPath(path: String) {
        results = listOf(get(path))
    }

    @Then("each answers 200 with Content-Type {string}")
    fun eachAnswers(type: String) {
        check(results.isNotEmpty())
        results.forEach {
            assertEquals(HttpStatus.OK, it.status, it.url.toString())
            assertEquals(type, it.responseHeaders.contentType.toString())
        }
    }

    @Then("each has Cache-Control {string}")
    fun eachCacheControl(value: String) = results.forEach { assertEquals(value, it.responseHeaders.cacheControl) }

    @Then("each body hashes to its address and matches its Repr-Digest")
    fun eachHashes() = results.forEach {
        val body = String(checkNotNull(it.responseBody), Charsets.UTF_8)
        assertEquals(it.url.path.substringAfterLast('/'), ContentAddress.address(body))
        assertEquals(ContentAddress.reprDigest(body), it.responseHeaders.getFirst("Repr-Digest"))
    }

    @Then("the response status is {int}")
    fun status(code: Int) = assertEquals(code, last.status.value())

    @Then("the response has an ETag")
    fun hasETag() {
        assertNotNull(last.responseHeaders.eTag)
    }

    @Then("the profile describes these relations")
    fun describes(table: DataTable) {
        val descriptors = ObjectMapper().readTree(last.responseBody).path("alps").path("descriptor")
        assertEquals(table.asMaps().map { it.getValue("relation") }, descriptors.map { it.path("id").asText() })
    }

    private fun get(path: String): EntityExchangeResult<ByteArray> =
        client.get().uri(path).exchange().expectBody().returnResult()
}
