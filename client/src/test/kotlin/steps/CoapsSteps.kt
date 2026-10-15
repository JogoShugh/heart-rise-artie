package steps

import heart.Credential
import heart.CredentialSources
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Steps for ARTIE over coaps, against a scripted DTLS server on this machine. */
class CoapsSteps(private val ctx: ClientContext) {
    private var server: FakeCoapServer? = null
    private val coaps: FakeCoapServer get() = checkNotNull(server) { "no coaps service in this scenario" }

    @After
    fun stop() {
        server?.stop()
    }

    @Given("a coaps service holds bed {int} {word}, admitting {string} with its key")
    fun service(@Suppress("UNUSED_PARAMETER") bed: Int, status: String, identity: String) {
        server = FakeCoapServer(status, identity to KEY)
    }

    @Given("ARTIE holds the coaps service's key for {string}")
    fun holdsKey(identity: String) {
        ctx.credentials = CredentialSources.of(mapOf(coaps.url to Credential.PreShared(identity, KEY)))
    }

    @Given("ARTIE holds a wrong key for {string} for the coaps service")
    fun holdsWrongKey(identity: String) {
        ctx.credentials = CredentialSources.of(mapOf(coaps.url to Credential.PreShared(identity, WRONG_KEY)))
    }

    @When("the client enters the coaps bed")
    fun enter() {
        ctx.current = ctx.client.enter(coaps.url)
    }

    @When("the client tries to enter the coaps bed")
    fun tryEnter() {
        ctx.lastError = runCatching { ctx.client.enter(coaps.url) }.exceptionOrNull()
    }

    @Then("the coaps service received {int} request(s)")
    fun received(count: Int) = assertEquals(count, coaps.requests.size)

    @Then("the coaps credential prints as {string}")
    fun prints(printed: String) = assertEquals(printed, Credential.PreShared("artie", KEY).toString())

    @Then("the coaps key appears in no event")
    fun keyInNoEvent() {
        val lines = (ctx.eventSink as MemoryEventSink).lines
        assertTrue(lines.isNotEmpty(), "no events were written")
        val hex = KEY.joinToString("") { "%02x".format(it) }
        val forms = listOf(String(KEY), java.util.Base64.getEncoder().encodeToString(KEY), hex)
        assertFalse(lines.any { line -> forms.any { it in line } }, "an event carried the key")
    }

    private companion object {
        val KEY = "coaps-test-key-for-artie".toByteArray()
        val WRONG_KEY = "wrong-key-for-artie-1234".toByteArray()
    }
}
