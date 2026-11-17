package com.artie.tolls.tck

import com.artie.heart.coap.HeartCoapAutoConfiguration
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import com.artie.tolls.bdd.InMemoryAccounts
import com.artie.tolls.bdd.TestCredentials
import com.artie.tolls.config.HeartConfig
import com.artie.tolls.domain.ACCOUNTS
import com.artie.tolls.domain.AccountEvent
import com.artie.tolls.domain.AccountSetup
import com.artie.tolls.store.InMemoryAccountDirectory
import io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME
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

/** Toll accounts run HEART's conformance features (heart-tck) against themselves. */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("heart/tck")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.artie.heart.tck,com.artie.tolls.tck")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary, message:build/cucumber/RunHeartTckTest.ndjson")
// The bookmark offers no act here, so the @bookmark-act scenarios do not apply.
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @bookmark-act")
class RunHeartTckTest

/** Account 1, suspended below zero: adding funds is offered, charging a trip is not. */
@TestConfiguration
class AccountFixture {
    @Bean
    fun accountTckFixture(accounts: InMemoryAccountDirectory, events: InMemoryEventStore): TckFixture =
        object : TckFixture {
            override val collection = "accounts"
            override val offered = TckAct(
                key = "tolls:add-funds",
                path = "add-funds",
                body = """{"amount": 20}""",
                invalidBody = """{"amount": 0}""",
                invalidPointer = "/amount",
            )
            override val absent = TckAct(
                key = "tolls:record-trip",
                path = "record-trip",
                body = """{"tag": "T-1001", "plaza": "harbor-bridge", "vehicleClass": "car", "time": "peak"}""",
                reason = "The balance is below zero, so the tags no longer pay; add funds first.",
            )
            override val credential = TestCredentials.CREDENTIAL
            override val searchTerm = "Miller"

            override suspend fun ready(): Long {
                accounts.clear()
                events.forget(ACCOUNTS.stream)
                accounts.add(AccountSetup(1, "Pat Miller"))
                events.append(ACCOUNTS, 1, 1, AccountEvent.FundsAdded(2_500))
                events.append(ACCOUNTS, 1, 2, AccountEvent.TagAdded("T-1001"))
                events.append(
                    ACCOUNTS,
                    1,
                    3,
                    AccountEvent.TripCharged("T-1001", "harbor-bridge", "five-axle-truck", "peak", 3_500),
                )
                return 1
            }
        }
}

@CucumberContextConfiguration
@WebFluxTest
@Import(
    InMemoryAccounts::class,
    AccountFixture::class,
    TckWorld::class,
    HeartConfig::class,
    HeartAutoConfiguration::class,
    HeartCoapAutoConfiguration::class,
    TestCredentials::class,
)
@TestPropertySource(properties = ["heart.coap.port=-1"])
class AccountTckContext
