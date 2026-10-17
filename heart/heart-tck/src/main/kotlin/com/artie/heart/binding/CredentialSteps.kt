package com.artie.heart.binding

import com.artie.heart.HeartResource
import com.artie.heart.Layers
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.Base64

/** Steps for who a HEART service admits, and what it says to those it does not. */
class CredentialSteps(
    client: WebTestClient,
    @Qualifier(LampServer.BEAN) private val resource: HeartResource<*, *>,
    private val http: HttpWorld,
) {
    /** A client with no default credential; each request sets its own, or none. */
    private val bare = client.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()

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

    /** The same bytes and headers, but the Date a server stamps on each response. */
    @Then("the last three responses have identical bytes and headers")
    fun identical() {
        val (a, b, c) = http.results.takeLast(THREE)
        listOf(b, c).forEach { other ->
            assertEquals(a.status, other.status)
            assertEquals(headers(a), headers(other))
            assertArrayEquals(a.responseBody, other.responseBody)
        }
    }

    @Then("the response body does not contain {string}")
    fun bodyLacks(text: String) =
        assertFalse(String(http.last.responseBody ?: ByteArray(0)).contains(text), "body mentions $text")

    private fun headers(result: EntityExchangeResult<ByteArray>): Map<String, List<String>> =
        result.responseHeaders.filterKeys { !it.equals(HttpHeaders.DATE, ignoreCase = true) }

    private fun send(method: String, path: String, authorization: String?) {
        val spec = when (method) {
            "POST" -> bare.post().uri(path).header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue("{}")
            else -> bare.get().uri(path)
        }
        authorization?.let { spec.header(HttpHeaders.AUTHORIZATION, it) }
        http.results = http.results + spec.exchange().expectBody().returnResult()
    }

    private companion object {
        const val THREE = 3
    }
}

/** The credentials a binding is configured with: read, or refused at the start. */
class CredentialConfigurationSteps(private val reader: CredentialReader) {
    private var configured: ((String?) -> Boolean)? = null
    private var failure: Throwable? = null

    @When("the service is configured with users {string} and tokens {string}")
    fun configure(users: String, tokens: String) {
        val result = runCatching { reader.read(users, tokens) }
        configured = result.getOrNull()
        failure = result.exceptionOrNull()
    }

    @Then("the configuration admits Basic {string}")
    fun admitsBasic(userAndPassword: String) =
        assertTrue(admits("Basic " + Base64.getEncoder().encodeToString(userAndPassword.toByteArray())))

    @Then("the configuration admits Bearer {string}")
    fun admitsBearer(token: String) = assertTrue(admits("Bearer $token"))

    @Then("the configuration fails with {string}")
    fun failsWith(text: String) {
        val error = checkNotNull(failure) { "expected a failure" }
        assertTrue(error.message.orEmpty().contains(text), "failure was: ${error.message}")
    }

    private fun admits(authorization: String): Boolean = checkNotNull(configured) { "failed: $failure" }(authorization)
}
