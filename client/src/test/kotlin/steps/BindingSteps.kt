package steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Steps for SPEC §6.0: a second binding, chosen by URI scheme, with no HTTP in it. */
class BindingSteps(private val ctx: ClientContext) {

    private lateinit var memory: MemoryBinding

    @Given("the {string} binding holds bed {int} {word} at {string}")
    fun binding(scheme: String, @Suppress("UNUSED_PARAMETER") bed: Int, status: String, url: String) {
        memory = MemoryBinding(url, status)
        ctx.bindings[scheme] = memory
    }

    @Given("someone else sows the {string} bed")
    fun someoneElseSows(@Suppress("UNUSED_PARAMETER") scheme: String) = memory.sow()

    @Then("the {string} bed is {word}")
    fun bedIs(@Suppress("UNUSED_PARAMETER") scheme: String, status: String) = assertEquals(status, memory.status)

    @Then("the client holds the {string} bed's current validator")
    fun holdsValidator(@Suppress("UNUSED_PARAMETER") scheme: String) =
        assertEquals(memory.validator, ctx.client.current?.etag)

    @Then("the client holds {string}")
    fun holds(url: String) = assertEquals(url, ctx.client.current?.url)

    @Then("nothing went over HTTP")
    fun nothingOverHttp() = assertTrue(ctx.transport.requests.isEmpty(), "HTTP requests: ${ctx.transport.requests}")
}
