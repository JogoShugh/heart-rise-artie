package steps

import heart.Exploration
import heart.ExploreSamples
import heart.Explorer
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.assertTrue

/** The explorer against a running server, for any domain: ARTIE_BASE_URL, ARTIE_SAMPLES, ARTIE_RESET. */
class LiveExploreSteps(private val ctx: ClientContext) {
    private lateinit var exploration: Exploration

    /** Names the scenario in each client's User-Agent, so Buster tells the runs apart. */
    @Before("@explore")
    fun useLiveServer(scenario: Scenario) {
        ctx.baseUrl = System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080"
        ctx.scenario = "explore-" + scenario.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }

    @Given("the server is at its seed state")
    fun seedState() {
        val reset = System.getenv("ARTIE_RESET")
        check(!reset.isNullOrBlank()) { "ARTIE_RESET names no reset command" }
        val process = ProcessBuilder(reset).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "$reset failed: $output" }
    }

    @When("ARTIE explores the running server with its samples")
    fun explore() {
        exploration = Explorer(ctx.client).explore(checkNotNull(ctx.baseUrl), samples())
        ctx.client.done()
    }

    @When("ARTIE explores the running server over CoAP with its samples")
    fun exploreOverCoap() {
        val host = java.net.URI(checkNotNull(ctx.baseUrl)).host
        val port = System.getenv("ARTIE_COAP_PORT") ?: "5683"
        exploration = Explorer(ctx.client).explore("coap://$host:$port", samples())
        ctx.client.done()
    }

    @Then("it took at least one act")
    fun tookActs() = assertTrue(exploration.acts.isNotEmpty(), "explored: $exploration")

    private fun samples(): ExploreSamples {
        val path = System.getenv("ARTIE_SAMPLES")
        check(!path.isNullOrBlank()) { "ARTIE_SAMPLES names no samples file" }
        return ExploreSamples.parse(File(path).readText())
    }
}
