package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Switches @live scenarios to real HTTP, and drives the second client. */
class LiveSteps(private val ctx: ClientContext) {

    @Before("@live")
    fun useLiveServer() {
        ctx.baseUrl = System.getenv("ARTIE_BASE_URL") ?: "http://localhost:8080"
    }

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
