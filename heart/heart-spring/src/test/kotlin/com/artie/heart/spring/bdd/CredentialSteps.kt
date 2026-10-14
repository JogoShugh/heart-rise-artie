package com.artie.heart.spring.bdd

import com.artie.heart.Layers
import com.artie.heart.HeartResource
import com.artie.heart.spring.HeartCredentials
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.Base64

/** Every scenario's client carries the lamp server's Bearer token, unless a step says otherwise. */
@TestConfiguration
class TestCredentials {
    @Bean
    fun bearerByDefault() = WebTestClientBuilderCustomizer {
        it.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $LAMP_TOKEN")
    }

    companion object {
        const val LAMP_TOKEN = "lamp-token"
        const val USERS = "coach:\$2y\$10\$8aPKQ4NLZ03c0ql39PXrZuIWta4FhSekHlrDmcbAAxxnhLXovjO8C"
        const val TOKENS = "a1011f8452b5270bb1b7dd59ea154bca26711ace2c21d4607c0fd4d5b26d14cb"
    }
}

/** Steps for who a HEART service admits, and what it says to those it does not. */
class CredentialSteps(
    client: WebTestClient,
    private val resource: HeartResource<*, *>,
    private val http: HttpWorld,
) {
    /** A client with no default credential; each request sets its own, or none. */
    private val bare = client.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
    private var configured: HeartCredentials? = null
    private var failure: Throwable? = null

    @When("a client with Basic {string} sends {word} {string}")
    fun sendBasic(userAndPassword: String, method: String, path: String) =
        send(method, path, "Basic " + Base64.getEncoder().encodeToString(userAndPassword.toByteArray()))

    @When("a client with Bearer {string} sends {word} {string}")
    fun sendBearer(token: String, method: String, path: String) = send(method, path, "Bearer $token")

    @When("a client with the Authorization {string} sends {word} {string}")
    fun sendRaw(value: String, method: String, path: String) = send(method, path, value)

    @When("a client with no credential sends {word} {string}")
    fun sendNone(method: String, path: String) = send(method, path, null)

    @When("^a client with no credential gets every (affordance set|schema|explanation) of the lamp by its address$")
    fun getEveryBare(kind: String) {
        val layers = resource.layers
        val (prefix, bodies) = when (kind) {
            "affordance set" -> Layers.AFFORDANCE_SETS to layers.affordanceSets
            "schema" -> Layers.SCHEMAS to layers.schemas
            else -> Layers.EXPLANATIONS to layers.explanations
        }
        http.results = bodies.keys.map { bare.get().uri(prefix + it).exchange().expectBody().returnResult() }
    }

    @Then("the response offers the challenges {string} and {string}")
    fun challenges(first: String, second: String) =
        assertEquals(listOf(first, second), http.last.responseHeaders[HttpHeaders.WWW_AUTHENTICATE])

    @Then("the last three responses have identical bytes and headers")
    fun identical() {
        val (a, b, c) = http.results.takeLast(3)
        listOf(b, c).forEach { other ->
            assertEquals(a.status, other.status)
            assertEquals(a.responseHeaders, other.responseHeaders)
            assertArrayEquals(a.responseBody, other.responseBody)
        }
    }

    @Then("the response body does not contain {string}")
    fun bodyLacks(text: String) =
        assertFalse(String(http.last.responseBody ?: ByteArray(0)).contains(text), "body mentions $text")

    @When("the service is configured with users {string} and tokens {string}")
    fun configure(users: String, tokens: String) {
        val result = runCatching { HeartCredentials.parse(users, tokens) }
        configured = result.getOrNull()
        failure = result.exceptionOrNull()
    }

    @Then("the configuration admits Basic {string}")
    fun admitsBasic(userAndPassword: String) = assertTrue(
        checkNotNull(configured) { "failed: $failure" }
            .admits("Basic " + Base64.getEncoder().encodeToString(userAndPassword.toByteArray())),
    )

    @Then("the configuration admits Bearer {string}")
    fun admitsBearer(token: String) =
        assertTrue(checkNotNull(configured) { "failed: $failure" }.admits("Bearer $token"))

    @Then("the configuration fails with {string}")
    fun failsWith(text: String) {
        val error = checkNotNull(failure) { "expected a failure" }
        assertTrue(error.message.orEmpty().contains(text), "failure was: ${error.message}")
    }

    private fun send(method: String, path: String, authorization: String?) {
        val spec = when (method) {
            "POST" -> bare.post().uri(path).header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}")
            else -> bare.get().uri(path)
        }
        authorization?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
        val result: EntityExchangeResult<ByteArray> = spec.exchange().expectBody().returnResult()
        http.results = http.results + result
    }
}
