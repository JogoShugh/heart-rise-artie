package com.artie.heart.gateway.bdd

import com.artie.heart.gateway.GatewayConfig
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

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
    fun upstream(url: String) = assertEquals(url, config.upstreamUrl)

    @Then("the description is read from {string}")
    fun description(spec: String) = assertEquals(spec, config.spec)

    @Then("the gateway calls the upstream with no credential")
    fun noCredential() = assertNull(config.auth.authorization())

    @Then("the gateway calls the upstream with {string}")
    fun credential(authorization: String) = assertEquals(authorization, config.auth.authorization())

    @Then("the relations are named in the profile {string}")
    fun profile(name: String) = assertEquals(name, config.profile)

    @Then("the gateway listens on port {int}")
    fun port(port: Int) = assertEquals(port, config.port)

    @Then("it refuses to start, saying {string}")
    fun refuses(says: String) = assertEquals(says, read.exceptionOrNull()?.message)
}
