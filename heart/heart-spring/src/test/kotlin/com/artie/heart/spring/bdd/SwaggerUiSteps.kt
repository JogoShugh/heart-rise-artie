package com.artie.heart.spring.bdd

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient

/** The Swagger UI page at /docs, and the files it loads. */
class SwaggerUiSteps(client: WebTestClient, private val http: HttpWorld) {
    /** No credential, and room for Swagger UI's bundle, which is larger than the default buffer. */
    private val bare = client.mutate()
        .defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }
        .codecs { it.defaultCodecs().maxInMemorySize(BUNDLE_BYTES) }
        .build()

    @Then("the page loads Swagger UI from this server and points it at {string}")
    fun pointsAt(document: String) {
        val page = String(checkNotNull(http.last.responseBody))
        assertTrue(files(page).isNotEmpty(), "the page loads no Swagger UI file")
        assertTrue(files(page).all { it.startsWith("/webjars/swagger-ui/") }, "files: ${files(page)}")
        assertTrue("url: \"$document\"" in page, "the page does not point at $document")
        assertTrue(SRC.findAll(page).none { it.groupValues[1].startsWith("http") }, "the page loads from elsewhere")
    }

    @When("a client with no credential gets each Swagger UI file the page loads")
    fun getFiles() {
        val page = String(checkNotNull(bare.get().uri("/docs").exchange().expectBody().returnResult().responseBody))
        http.results = files(page).map { bare.get().uri(it).exchange().expectBody().returnResult() }
    }

    @Then("each answers 200")
    fun eachOk() {
        assertTrue(http.results.isNotEmpty())
        http.results.forEach { assertEquals(200, it.status.value(), it.url.toString()) }
    }

    private fun files(page: String) = SRC.findAll(page).map { it.groupValues[1] }.toList()

    private companion object {
        val SRC = Regex("""(?:src|href)="([^"]+)"""")
        const val BUNDLE_BYTES = 8 * 1024 * 1024
    }
}
