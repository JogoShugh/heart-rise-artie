package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.Rung
import io.cucumber.java.Before
import io.cucumber.java.Scenario
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Switches @live scenarios to real HTTP, and drives the second client. */
class LiveSteps(private val ctx: ClientContext) {

    /** Also names the scenario in each client's User-Agent, so a filter such as Buster tells runs apart. */
    @Before("@live")
    fun useLiveServer(scenario: Scenario) {
        ctx.baseUrl = System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080"
        ctx.scenario = scenario.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }

    /** Runs ARTIE_RESET (server-spring/reset-db.sh by default, set by the liveTest task). */
    @Given("the beds are at their seed state")
    fun seedState() {
        val reset = System.getenv("ARTIE_RESET")
        check(!reset.isNullOrBlank()) { "ARTIE_RESET names no reset command" }
        val process = ProcessBuilder(reset).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "$reset failed: $output" }
    }

    @Then("the client asked for the rung the server serves when it entered {string}")
    fun askedForServedRung(url: String) {
        val entered = ctx.wireLog.exchanges.map { it.first }.last { it.method == "GET" && it.url == ctx.url(url) }
        assertEquals(servedRung().prefer, entered.header("Prefer"))
    }

    @Then("the current representation is at the rung the server serves")
    fun atServedRung() = assertEquals(servedRung(), ctx.client.appliedRung)

    /** The live server's cap, from the same variable it was started with. */
    private fun servedRung(): Rung = Rung.valueOf(System.getenv("HEART_MAX_RUNG") ?: "L2")

    @Given("a second client enters {string}")
    fun secondEnters(url: String) {
        ctx.second.enter(ctx.url(url))
    }

    @When("the second client acts on {string} with:")
    fun secondActs(rel: String, yaml: String) {
        val payload = Json.parseToJsonElement(jsonMapper.writeValueAsString(yamlMapper.readTree(yaml))).jsonObject
        ctx.second.act(rel, payload)
    }

    @Then("the client now holds the same ETag as the second client")
    fun sameETag() {
        val mine = assertNotNull(ctx.client.current?.etag, "client holds no ETag")
        assertEquals(ctx.second.current?.etag, mine)
    }

    @Then("the wire carried {int} requests, the last answered {int}")
    fun wireCarried(count: Int, lastStatus: Int) {
        val log = ctx.wireLog.exchanges.map { (request, status) -> "${request.method} ${request.url} -> $status" }
        assertEquals(count, log.size, "wire: $log")
        assertEquals(lastStatus, ctx.wireLog.exchanges.last().second, "wire: $log")
    }

    private companion object {
        val yamlMapper = ObjectMapper(YAMLFactory())
        val jsonMapper = ObjectMapper()
    }
}
