package steps

import heart.HeartRequest
import heart.UserAgentTransport
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Steps for the User-Agent each ARTIE client sends. */
class UserAgentSteps(private val ctx: ClientContext) {

    @When("a GET {string} with User-Agent {string} goes through ARTIE's naming")
    fun sendNamed(url: String, agent: String) {
        UserAgentTransport(next = ctx.transport).send(HeartRequest("GET", url, mapOf("user-agent" to agent)))
    }

    @Then("every request on the wire had a User-Agent starting with {string}")
    fun everyRequestNamed(prefix: String) {
        val agents = ctx.transport.requests.map { it.header("User-Agent") }
        assertTrue(agents.isNotEmpty() && agents.all { it?.startsWith(prefix) == true }, "User-Agents: $agents")
    }

    @Then("the two requests on the wire had different User-Agent values")
    fun twoDifferentAgents() {
        val agents = ctx.transport.requests.map { it.header("User-Agent") }
        assertEquals(2, agents.size, "User-Agents: $agents")
        assertTrue(agents[0] != agents[1], "User-Agents: $agents")
    }
}
