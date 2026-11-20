package com.artie.heart.binding

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.web.reactive.server.WebTestClient

/** A browser with no credential, and the page it is refused with (credentials.feature). */
class SignInSteps(client: WebTestClient, private val http: HttpWorld) {
    private val bare = client.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()

    @When("a browser with no credential sends {word} {string}")
    fun browserSends(method: String, path: String) {
        http.results = http.results +
            bare.method(HttpMethod.valueOf(method)).uri(path).header(HttpHeaders.ACCEPT, HttpWorld.BROWSER)
                .exchange().expectBody().returnResult()
    }

    @Then("the page asks the browser to sign in to {string}")
    fun asksToSignIn(realm: String) {
        val page = Jsoup.parse(String(http.last.responseBody ?: ByteArray(0)))
        assertEquals("Sign in to $realm", page.selectFirst("h1")?.text())
        val link = page.selectFirst("a.sign-in")
        assertTrue(link != null && link.attr("href").isEmpty(), page.html())
    }
}
