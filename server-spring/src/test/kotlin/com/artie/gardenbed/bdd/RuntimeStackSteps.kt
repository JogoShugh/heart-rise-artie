package com.artie.gardenbed.bdd

import com.artie.gardenbed.GardenBedApplication
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.SpringApplication

/**
 * Asks Spring Boot which web stack the application would start, the same
 * deduction `SpringApplication.run` makes from the classpath, without
 * starting it. The test classpath carries the runtime classpath.
 */
class RuntimeStackSteps {

    @Then("the server application's web stack is {string}")
    fun webStack(expected: String) =
        assertEquals(expected, SpringApplication(GardenBedApplication::class.java).webApplicationType.name)

    @Then("no servlet container is on the runtime classpath")
    fun noServletContainer() {
        val present = SERVLET_CONTAINERS.filter { runCatching { Class.forName(it) }.isSuccess }
        assertTrue(present.isEmpty(), "servlet container classes found: $present")
    }

    private companion object {
        val SERVLET_CONTAINERS = listOf(
            "org.apache.catalina.startup.Tomcat",
            "org.eclipse.jetty.server.Server",
            "io.undertow.Undertow",
        )
    }
}
