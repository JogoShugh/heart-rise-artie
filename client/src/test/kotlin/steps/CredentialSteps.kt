package steps

import heart.AuthTransport
import heart.ClaudeReasoner
import heart.Credential
import heart.CredentialSource
import heart.CredentialSources
import heart.EnvironmentCredentials
import heart.HeartRequest
import heart.Origin
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A source that gives one credential, and another when asked to renew it. */
class RenewingSource(private val origin: Origin, private val first: Credential, private val next: Credential) :
    CredentialSource {
    override fun credentialFor(origin: Origin): Credential? = first.takeIf { origin == this.origin }
    override fun renewed(origin: Origin): Credential? = next.takeIf { origin == this.origin }
}

/** Steps for the credentials ARTIE sends, and where they must never appear. */
class CredentialSteps(private val ctx: ClientContext) {

    @Given("ARTIE holds the {word} credential {string} for {string}")
    fun holds(scheme: String, value: String, origin: String) {
        ctx.credentials = CredentialSources.of(mapOf(origin to credential(scheme, value)))
    }

    @Given("ARTIE holds no credentials")
    fun holdsNone() {
        ctx.credentials = CredentialSources.NONE
    }

    @Given("ARTIE holds the Bearer credential {string} for {string}, renewed as {string}")
    fun holdsRenewing(first: String, origin: String, next: String) {
        ctx.credentials = RenewingSource(Origin.of(origin), Credential.Bearer(first), Credential.Bearer(next))
    }

    @When("a GET {string} with Authorization {string} goes through ARTIE's credentials")
    fun sendWithOwn(url: String, value: String) {
        AuthTransport(ctx.credentials, ctx.transport).send(HeartRequest("GET", url, mapOf("authorization" to value)))
    }

    @Then("the request to {string} carried Authorization {string}")
    fun carried(url: String, value: String) = assertEquals(value, requestTo(url).header(AUTHORIZATION))

    @Then("the request to {string} carried no Authorization")
    fun carriedNone(url: String) = assertNull(requestTo(url).header(AUTHORIZATION))

    @Then("the requests carried Authorization {string}, then {string}")
    fun carriedInTurn(first: String, second: String) =
        assertEquals(listOf(first, second), ctx.transport.requests.map { it.header(AUTHORIZATION) })

    @Then("the client sent no request")
    fun sentNone() = assertEquals(emptyList(), ctx.transport.requests.map { "${it.method} ${it.url}" })

    @Then("the client sent {int} request(s)")
    fun sentCount(count: Int) =
        assertEquals(count, ctx.transport.requests.size, "${ctx.transport.requests.map { "${it.method} ${it.url}" }}")

    @Then("the error does not contain {string}")
    fun errorLacks(text: String) {
        val error = assertNotNull(ctx.lastError, "expected an error, none was raised")
        assertFalse(error.toString().contains(text), "error was: $error")
    }

    @Then("the {word} credential {string} prints as {string}")
    fun prints(scheme: String, value: String, printed: String) {
        lastCredential = credential(scheme, value)
        assertEquals(printed, lastCredential.toString())
    }

    @Then("a request carrying it prints with {string} and without {string}")
    fun requestPrints(shown: String, hidden: String) {
        val request = HeartRequest("GET", "https://garden.test/beds/2", mapOf(AUTHORIZATION to lastCredential.header()))
        val text = request.toString()
        assertTrue(text.contains(shown), text)
        assertFalse(text.contains(hidden) || text.contains(lastCredential.header()), text)
    }

    @Then("{string} appears on the wire only in the Authorization header of requests to {string}")
    fun onlyInOwnHeader(secret: String, origin: String) {
        val own = Origin.of(origin)
        ctx.transport.requests.forEach { request ->
            val elsewhere = request.copy(headers = request.headers.filterKeys { !it.equals(AUTHORIZATION, true) })
            assertFalse(elsewhere.url.contains(secret) || elsewhere.body.orEmpty().contains(secret), "${request.url}")
            assertFalse(elsewhere.headers.values.any { it.contains(secret) }, "a header of ${request.url}")
            if (Origin.of(request.url) != own) assertNull(request.header(AUTHORIZATION), request.url)
        }
        assertTrue(ctx.transport.requests.any { it.header(AUTHORIZATION).orEmpty().contains(secret) })
    }

    @Then("{string} appears in no Claude request")
    fun notInClaude(secret: String) {
        val bodies = ctx.transport.requests.filter { it.url == ClaudeReasoner.ENDPOINT }
        assertTrue(bodies.isNotEmpty(), "no Claude request was sent")
        bodies.forEach { request ->
            assertFalse(request.body.orEmpty().contains(secret), "Claude request body")
            assertNull(request.header(AUTHORIZATION), "Claude request carried Authorization")
        }
    }

    @Then("{string} appears in no event")
    fun notInEvents(secret: String) {
        val lines = (ctx.eventSink as MemoryEventSink).lines
        assertTrue(lines.isNotEmpty(), "no events were written")
        assertFalse(lines.any { it.contains(secret) }, "an event line carried the secret")
    }

    // ---- where credentials come from ----

    private val environment = mutableMapOf<String, String>()
    private var source: CredentialSource? = null
    private var failure: Throwable? = null

    @Then("the credential variables for {string} are {string}, {string} and {string}")
    fun variables(origin: String, token: String, user: String, password: String) {
        val names = EnvironmentCredentials.namesFor(Origin.of(origin))
        assertEquals(listOf(token, user, password), listOf(names.token, names.user, names.password))
    }

    @Given("the environment holds:")
    fun environmentHolds(rows: List<List<String?>>) {
        rows.forEach { (name, value) -> environment[checkNotNull(name)] = value.orEmpty() }
    }

    @When("ARTIE reads credentials from the environment")
    fun readEnvironment() {
        source = CredentialSources.fromEnvironment(environment)
    }

    @When("ARTIE tries to read credentials from the environment")
    fun tryReadEnvironment() {
        failure = runCatching { CredentialSources.fromEnvironment(environment) }.exceptionOrNull()
    }

    @Then("the credential for {string} is the {word} credential {string}")
    fun credentialIs(origin: String, scheme: String, value: String) =
        assertEquals(credential(scheme, value), checkNotNull(source).credentialFor(Origin.of(origin)))

    @Then("the credential for {string} is none")
    fun credentialIsNone(origin: String) = assertNull(checkNotNull(source).credentialFor(Origin.of(origin)))

    @Then("reading credentials failed with {string}")
    fun failedWith(text: String) {
        val error = assertNotNull(failure, "expected a failure, none was raised")
        assertTrue(error.message.orEmpty().contains(text), "failure was: ${error.message}")
    }

    /** An empty value cannot leak, so there is nothing to look for. */
    @Then("the failure does not contain {string}")
    fun failureLacks(text: String) {
        val error = assertNotNull(failure, "expected a failure, none was raised")
        if (text.isNotEmpty()) assertFalse(error.toString().contains(text), "failure was: $error")
    }

    private lateinit var lastCredential: Credential

    private fun requestTo(url: String): HeartRequest =
        ctx.transport.requests.lastOrNull { it.url == url }
            ?: error("no request to $url among ${ctx.transport.requests.map { it.url }}")

    private companion object {
        const val AUTHORIZATION = "Authorization"

        fun credential(scheme: String, value: String): Credential = when (scheme) {
            "Bearer" -> Credential.Bearer(value)
            "Basic" -> Credential.Basic(value.substringBefore(':'), value.substringAfter(':'))
            else -> error("unknown scheme $scheme")
        }
    }
}
