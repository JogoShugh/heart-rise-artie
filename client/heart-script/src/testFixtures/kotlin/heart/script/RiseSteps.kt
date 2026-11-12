package heart.script

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * Steps for a script that asks with a reply (rise.feature): a host that keeps replies in the scripted service, and
 * says a reply changed once the service's next read of it would show a new state. Each wait moves the run's clock
 * by half a minute.
 */
class RiseSteps(private val steps: ScriptSteps) {
    private val told = mutableListOf<List<String>>()

    @Given("the host keeps replies, opening {string} with its keys at {string}")
    fun keeps(address: String, keys: String) {
        steps.replies = object : Replies {
            override fun open() = Reply(address, keys).also { told += listOf("opened", address, "") }

            override fun asked(reply: Reply, request: String) {
                told += listOf("asked", reply.address, request)
            }

            override fun await(reply: Reply, atMost: Duration): Boolean {
                told += listOf("waited", reply.address, "")
                steps.run.clock = steps.run.clock().plusSeconds(HALF_MINUTE).let { later -> { later } }
                return reply.address in steps.service.settles
            }
        }
    }

    @Given("the host keeps no replies")
    fun keepsNone() {
        steps.replies = null
    }

    @Then("trace step {int} expected {string}")
    fun traceExpected(number: Int, expected: String) {
        assertEquals(expected, steps.run.trace[number - 1].expect)
    }

    @Then("the run's scenarios were")
    fun scenariosWere(table: DataTable) {
        assertEquals(table.asMaps(), steps.run.scenarios.map { mapOf("scenario" to it.name, "passed" to "${it.passed}") })
    }

    @Then("the host was told, in order")
    fun toldInOrder(table: DataTable) {
        assertEquals(table.asLists().drop(1).map { row -> row.map { it ?: "" } }, told)
    }

    private companion object {
        const val HALF_MINUTE = 30L
    }
}
