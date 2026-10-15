package com.artie.heart.http4k.binding

import com.artie.heart.HeartResource
import com.artie.heart.ServingLimits
import com.artie.heart.binding.CredentialReader
import com.artie.heart.binding.HttpWorld
import com.artie.heart.binding.LampServer
import com.artie.heart.binding.LampStore
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import io.cucumber.spring.CucumberContextConfiguration
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The lamp server under heart-tck's binding features, served by heart-http4k
 * on a port the system picks. It links each plain answer to its state's
 * affordance set (plain-json.feature), and lists colours (listings.feature).
 */
@Configuration(proxyBeanMethods = false)
class LampBinding {
    @Bean
    fun store() = LampStore()

    @Bean
    fun lamps(store: LampStore): HeartResource<*, *> = LampServer.resource(store)

    /** One object for the run: the binding reads it on every request, and the features change it. */
    @Bean
    fun limits() = ServingLimits(affordanceLinks = true)

    @Bean
    fun credentialReader() = CredentialReader { users, tokens -> Credentials.parse(users, tokens)::admits }

    @Bean(destroyMethod = "stop")
    fun server(lamps: HeartResource<*, *>, limits: ServingLimits): Http4kServer =
        HeartHttp(
            listOf(lamps),
            Credentials.parse(LampServer.USERS, LampServer.TOKENS),
            HeartSettings(limits = limits, listings = listOf(LampServer.colours())),
        ).asServer(Jetty(0)).start()

    /** Every scenario's client carries the lamp server's Bearer token, unless a step says otherwise. */
    @Bean
    fun client(server: Http4kServer): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:${server.port()}")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${LampServer.LAMP_TOKEN}")
            .build()
}

@CucumberContextConfiguration
@ContextConfiguration(classes = [LampBinding::class, HttpWorld::class])
class BindingSpringConfig
