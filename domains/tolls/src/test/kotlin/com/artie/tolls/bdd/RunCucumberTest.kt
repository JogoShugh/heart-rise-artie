package com.artie.tolls.bdd

import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.tolls.config.HeartConfig
import com.artie.tolls.domain.AccountService
import com.artie.tolls.store.InMemoryAccountDirectory
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME
import io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME
import io.cucumber.spring.CucumberContextConfiguration
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

/** Runs the toll account features. The bdd-gates log task reads this class's JUnit XML. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.tolls.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary, message:build/cucumber/RunCucumberTest.ndjson")
class RunCucumberTest

/** Accounts and their events in memory: the real rules and the real HEART library, no Postgres. */
@TestConfiguration
class InMemoryAccounts {
    @Bean
    fun accountDirectory() = InMemoryAccountDirectory()

    @Bean
    fun eventStore() = InMemoryEventStore()

    @Bean
    fun accountService(accounts: InMemoryAccountDirectory, events: InMemoryEventStore) = AccountService(
        accounts,
        events
    )
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryAccounts::class,
    HeartConfig::class,
    HeartAutoConfiguration::class,
    HeartCoapAutoConfiguration::class,
    TestCredentials::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class CucumberSpringConfig
