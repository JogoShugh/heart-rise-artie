package com.artie.heart.spring.bdd

import com.artie.heart.HeartResource
import com.artie.heart.binding.CredentialReader
import com.artie.heart.binding.HttpWorld
import com.artie.heart.binding.LampServer
import com.artie.heart.binding.LampStore
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.spring.HeartCredentials
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.reactive.server.WebTestClientBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders

/**
 * The lamp server under heart-tck's binding features: a server that declares
 * one resource type, the lamp, and a listing, and writes no controllers.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class LampSpringServer {
    @Bean
    fun lampStore() = LampStore()

    @Bean
    fun lamps(store: LampStore): HeartResource<*, *> = LampServer.resource(store)

    @Bean
    fun colours() = LampServer.colours()

    @Bean
    fun credentialReader() = CredentialReader { users, tokens -> HeartCredentials.parse(users, tokens)::admits }
}

/** Every scenario's client carries the lamp server's Bearer token, unless a step says otherwise. */
@TestConfiguration
class BearerByDefault {
    @Bean
    fun lampToken() = WebTestClientBuilderCustomizer {
        it.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${LampServer.LAMP_TOKEN}")
    }
}

@CucumberContextConfiguration
@WebFluxTest(
    properties = [
        "heart.auth.basic.users=${LampServer.USERS}",
        "heart.auth.bearer.tokens=${LampServer.TOKENS}",
        // The lamp server links each plain answer to its state's affordance set (plain-json.feature).
        "heart.affordance-links=true",
    ],
)
@Import(LampSpringServer::class, HeartAutoConfiguration::class, HttpWorld::class, BearerByDefault::class)
class CucumberSpringConfig
