package com.artie.tickets.bdd

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders

/**
 * Every test client carries the test service's Bearer token. The service admits
 * it by its SHA-256, set in the test application.properties.
 */
@TestConfiguration
class TestCredentials {
    @Bean
    fun bearerByDefault() = WebTestClientBuilderCustomizer {
        it.defaultHeader(HttpHeaders.AUTHORIZATION, CREDENTIAL)
    }

    companion object {
        const val CREDENTIAL = "Bearer tickets-test-token"
    }
}
