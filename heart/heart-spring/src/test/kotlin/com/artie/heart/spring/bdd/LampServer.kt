package com.artie.heart.spring.bdd

import com.artie.heart.Layers
import com.artie.heart.fixtures.Lamps
import com.artie.heart.spring.HeartAutoConfiguration
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/** A server that declares one resource type, the lamp, and nothing else: no controllers of its own. */
@SpringBootConfiguration
@EnableAutoConfiguration
class LampServer {
    @Bean
    fun lamps(): Layers<*, *> = Layers(Lamps.type(Lamps.profile("home")))
}

@CucumberContextConfiguration
@WebFluxTest
@Import(LampServer::class, HeartAutoConfiguration::class)
class CucumberSpringConfig
