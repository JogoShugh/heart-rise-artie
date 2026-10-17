package steps

import heart.Credential
import heart.CredentialSource
import heart.CredentialSources
import heart.EnvironmentCredentials
import heart.FileCredentials
import heart.HttpTransport
import heart.Origin
import heart.VaultCredentials
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Steps for scripts/auth-check.sh: each source alone, against the real
 * services, with credentials made for the run. The script passes them in
 * ARTIE_CHECK_* variables; nothing here reads ~/.artie or ARTIE_* variables.
 */
class AuthLiveSteps(private val ctx: ClientContext) {

    @Before("@auth-live")
    fun live() {
        ctx.baseUrl = bookmark("garden").trimEnd('/')
    }

    @Given("^ARTIE's only credential source is (the environment|the credentials file|Vault), " +
        "holding the (garden|basketball)'s (Bearer|Basic) credential$")
    fun onlySource(source: String, service: String, scheme: String) {
        val origin = Origin.of(bookmark(service))
        ctx.credentials = when (source) {
            "the environment" -> EnvironmentCredentials(variables(origin, service, scheme))
            "the credentials file" -> FileCredentials(file(origin, service, scheme))
            else -> vault(service, scheme)
        }
    }

    @Given("ARTIE's only credential source is the environment, holding nothing")
    fun nothing() {
        ctx.credentials = EnvironmentCredentials(emptyMap())
    }

    @Given("ARTIE's only credential source is the environment, holding a Bearer token made up")
    fun madeUp() {
        ctx.credentials = CredentialSources.of(mapOf(bookmark("basketball") to Credential.Bearer("made-up-token")))
    }

    @When("the client enters the {word}'s bookmark")
    fun enter(service: String) {
        ctx.current = ctx.client.enter(bookmark(service))
    }

    @When("the client tries to enter the {word}'s bookmark")
    fun tryEnter(service: String) {
        ctx.lastError = runCatching { ctx.client.enter(bookmark(service)) }.exceptionOrNull()
    }

    @Then("the bookmark was read with a {word} credential")
    fun readWith(scheme: String) {
        val (request, status) = ctx.wireLog.exchanges.first()
        assertEquals(200, status, "${request.method} ${request.url}")
        val sent = request.header("Authorization").orEmpty()
        assertTrue(sent.startsWith("$scheme "), "sent ${sent.substringBefore(' ')}")
    }

    @Then("the run's {word} credentials appear in no event")
    fun noneInEvents(service: String) {
        val lines = (ctx.eventSink as MemoryEventSink).lines
        assertTrue(lines.isNotEmpty(), "no events were written")
        listOf("TOKEN", "PASSWORD").map { check(service, it) }.forEach { secret ->
            assertFalse(lines.any { secret in it }, "an event carried the $service credential")
        }
    }

    private fun variables(origin: Origin, service: String, scheme: String): Map<String, String> {
        val names = EnvironmentCredentials.namesFor(origin)
        return if (scheme == "Bearer") mapOf(names.token to check(service, "TOKEN"))
        else mapOf(names.user to check(service, "USER"), names.password to check(service, "PASSWORD"))
    }

    private fun file(origin: Origin, service: String, scheme: String): Path {
        val lines = if (scheme == "Bearer") listOf("bearer = ${check(service, "TOKEN")}")
        else listOf("user = ${check(service, "USER")}", "password = ${check(service, "PASSWORD")}")
        val file = Files.createTempDirectory("artie-auth-live").resolve("credentials")
        Files.writeString(file, (listOf("[$origin]") + lines).joinToString("\n"))
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        return file
    }

    /** The script put the garden's token and basketball's user in Vault; a row asks for what is there. */
    private fun vault(service: String, scheme: String): CredentialSource {
        val stored = if (service == "garden") "Bearer" else "Basic"
        check(scheme == stored) { "Vault holds the $service's $stored credential, not $scheme" }
        val env = mapOf("ARTIE_VAULT_ADDR" to check("VAULT", "ADDR"), "VAULT_TOKEN" to check("VAULT", "TOKEN"))
        val home = Files.createTempDirectory("artie-home")
        return checkNotNull(VaultCredentials.fromEnvironment(env, home, HttpTransport()))
    }

    private companion object {
        fun bookmark(service: String) = when (service) {
            "garden" -> System.getenv("ARTIE_CHECK_GARDEN_URL") ?: "http://localhost:8080/"
            else -> System.getenv("ARTIE_CHECK_BASKETBALL_URL") ?: "http://localhost:8181/"
        }

        fun check(service: String, what: String): String {
            val name = "ARTIE_CHECK_${service.uppercase()}_$what"
            return checkNotNull(System.getenv(name)) { "$name is not set; run scripts/auth-check.sh" }
        }
    }
}
