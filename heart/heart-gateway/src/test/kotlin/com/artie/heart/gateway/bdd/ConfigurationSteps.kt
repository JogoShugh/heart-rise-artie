package com.artie.heart.gateway.bdd

import com.artie.heart.Caller
import com.artie.heart.gateway.GatewayCare
import com.artie.heart.gateway.GatewayConfig
import com.artie.heart.gateway.UpstreamAuth
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** A gateway's configuration, read from an environment a scenario sets up. */
class ConfigurationSteps {
    private var environment: Map<String, String> = emptyMap()
    private var read: Result<GatewayConfig> = Result.failure(IllegalStateException("the configuration was not read"))

    private val config: GatewayConfig get() = read.getOrThrow()

    @Given("the environment")
    fun environment(table: DataTable) {
        environment = table.asLists().associate { (name, value) -> name to value.orEmpty() }
    }

    @When("the gateway reads its configuration")
    fun readConfiguration() {
        read = runCatching { GatewayConfig.from(environment) }
    }

    @Then("the upstream is {string}")
    fun upstream(url: String) = assertEquals(url, config.upstream.url)

    @Then("the description is read from {string}")
    fun description(spec: String) = assertEquals(spec, config.upstream.spec)

    @Then("the gateway calls the upstream with no credential")
    fun noCredential() = assertNull(config.upstream.auth.authorization())

    @Then("the gateway calls the upstream with {string}")
    fun credential(authorization: String) = assertEquals(authorization, config.upstream.auth.authorization())

    @Then("the relations are named in the profile {string}")
    fun profile(name: String) = assertEquals(name, config.profile)

    @Then("the connector is read from {string}")
    fun connector(path: String) = assertEquals(path, config.connector.toString())

    @Then("the relations are named by the connector's profile")
    fun connectorProfile() = assertNull(config.profile)

    @Then("the gateway has no connector")
    fun noConnector() = assertNull(config.connector)

    @Then("the gateway listens on port {int}")
    fun port(port: Int) = assertEquals(port, config.serving.port)

    @Then("the gateway calls the upstream with each caller's own credential")
    fun forwarded() {
        assertEquals(UpstreamAuth.Forwarded, config.upstream.auth)
        assertEquals("Bearer ada-token", Caller.during("Bearer ada-token") { config.upstream.auth.authorization() })
    }

    @Then("the gateway admits any Basic or Bearer credential, for the upstream to judge, and no request without one")
    fun admitsAny() {
        assertTrue(config.serving.credentials.admits("Bearer anyone's-token"))
        assertTrue(config.serving.credentials.admits("Basic YW55b25lOnNlY3JldA=="))
        assertFalse(config.serving.credentials.admits(null))
        assertFalse(config.serving.credentials.admits("Bearer "))
    }

    @Then("the gateway serves plain http")
    fun plainHttp() = assertNull(config.serving.tls)

    @Then("the gateway serves https from {string}")
    fun https(keystore: String) = assertEquals(keystore, config.serving.tls?.keystore?.toString())

    @Then("acts need a validator")
    fun validatorNeeded() = assertTrue(config.serving.limits.validatorRequired)

    @Then("acts may come with no validator")
    fun noValidatorNeeded() = assertFalse(config.serving.limits.validatorRequired)

    @Then("the gateway takes every kind of care")
    fun everyCare() = assertEquals(GatewayCare.EVERY, config.serving.care)

    @Then("the gateway takes care as {string}")
    fun care(care: String) = assertEquals(care, config.serving.care.toString())

    @Then("it refuses to start, saying {string}")
    fun refuses(says: String) = assertEquals(says, read.exceptionOrNull()?.message)
}
