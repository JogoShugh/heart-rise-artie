package steps

import heart.AuthTransport
import heart.ClaudeReasoner
import heart.Credential
import heart.CredentialSource
import heart.CredentialSources
import heart.EnvironmentCredentials
import heart.HeartRequest
import heart.HeartResponse
import heart.Origin
import heart.Transport
import kotlinx.serialization.json.Json
import java.net.ConnectException
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
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

    @Then("the requests carried Authorization in turn")
    fun carriedInTurn(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("authorization") },
        ctx.transport.requests.map { it.header(AUTHORIZATION) },
    )

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
        val header = checkNotNull(lastCredential.header())
        val request = HeartRequest("GET", "https://garden.test/beds/2", mapOf(AUTHORIZATION to header))
        val text = request.toString()
        assertTrue(text.contains(shown), text)
        assertFalse(text.contains(hidden) || text.contains(header), text)
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

    /** Where Vault's requests go: the scripted wire, unless a scenario says Vault cannot be reached. */
    private var vault: Transport = ctx.transport
    private var lastAsked: Credential? = null

    @Given("Vault answers {word} {string} with:")
    fun vaultAnswers(method: String, url: String, raw: String) = HttpSteps(ctx).serviceAnswers(method, url, raw)

    @Given("Vault cannot be reached")
    fun vaultUnreachable() {
        vault = object : Transport {
            override fun send(request: HeartRequest): HeartResponse = throw ConnectException("Connection refused")
        }
    }

    @Given("the environment does not hold {string}")
    fun environmentLacks(name: String) {
        environment.remove(name)
    }

    @Given("the home folder holds {string} with {string}")
    fun homeFile(name: String, content: String) {
        Files.writeString(home.resolve(name), content)
    }

    @Given("ARTIE_VAULT_SECRET_ID_FILE names a file with mode {string} holding {string}")
    fun secretIdFile(mode: String, content: String) {
        val folder = Files.createTempDirectory("artie-vault")
        writeFile(folder.resolve("secret-id"), mode, content)
        environment["ARTIE_VAULT_SECRET_ID_FILE"] = folder.resolve("secret-id").toString()
    }

    @Given("ARTIE holds the credentials it reads")
    fun holdsWhatItReads() {
        ctx.credentials = CredentialSources.standard(environment, home, vault)
    }

    @When("ARTIE tries to get the credential for {string}")
    fun tryGet(origin: String) {
        failure = runCatching { checkNotNull(source).credentialFor(Origin.of(origin)) }.exceptionOrNull()
    }

    @When("ARTIE gets the credential for {string} twice")
    fun getTwice(origin: String) {
        repeat(2) { lastAsked = checkNotNull(source).credentialFor(Origin.of(origin)) }
    }

    @When("ARTIE gets the credential for {string}, then a renewed one")
    fun getThenRenewed(origin: String) {
        checkNotNull(source).credentialFor(Origin.of(origin))
        lastAsked = checkNotNull(source).renewed(Origin.of(origin))
    }

    @Then("the credential for {string} was read from Vault {int} times, and is now the Bearer credential {string}")
    fun readTimes(origin: String, times: Int, now: String) {
        val own = Origin.of(origin)
        val path = "/v1/secret/data/artie/${own.host}_${own.port}"
        assertEquals(times, ctx.transport.requests.count { it.url.endsWith(path) })
        assertEquals(Credential.Bearer(now), lastAsked)
    }

    @Then("the request to {string} carried no {string} header")
    fun carriedNoHeader(url: String, name: String) = assertNull(requestTo(url).header(name))

    @Then("the request to {string} carried {string} {string}")
    fun carriedHeader(url: String, name: String, value: String) = assertEquals(value, requestTo(url).header(name))

    @Then("the AppRole logins sent the role {string} and the secret ID from the file")
    fun appRoleLogins(role: String) {
        val secretId = Files.readString(Path.of(checkNotNull(environment["ARTIE_VAULT_SECRET_ID_FILE"])))
        val logins = ctx.transport.requests.filter { it.url.endsWith("/v1/auth/approle/login") }
        assertTrue(logins.isNotEmpty())
        val expected = Json.parseToJsonElement("""{"role_id":"$role","secret_id":"$secretId"}""")
        logins.forEach { assertEquals(expected, Json.parseToJsonElement(it.body.orEmpty())) }
    }

    @Then("the credential variables for each origin are")
    fun variables(table: DataTable) = table.asMaps().forEach { row ->
        val names = EnvironmentCredentials.namesFor(Origin.of(row.getValue("origin")))
        val expected = listOf(row.getValue("token"), row.getValue("user"), row.getValue("password"))
        assertEquals(expected, listOf(names.token, names.user, names.password))
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

    /** A home folder for this scenario, so ~/.artie is never the real one. */
    private val home: Path by lazy { Files.createTempDirectory("artie-home") }

    @Given("ARTIE's folder holds a credentials file with mode {string}:")
    fun credentialsFile(mode: String, content: String) = writeCredentials(home.resolve(".artie"), mode, content)

    @Given("ARTIE_HOME names a folder holding a credentials file with mode {string}:")
    fun artieHome(mode: String, content: String) {
        val folder = Files.createTempDirectory("artie-elsewhere")
        environment["ARTIE_HOME"] = folder.toString()
        writeCredentials(folder, mode, content)
    }

    @Given("ARTIE's folder holds no credentials file")
    fun noCredentialsFile() = check(!Files.exists(home.resolve(".artie/credentials")))

    @When("ARTIE reads its credentials")
    fun readStandard() {
        source = CredentialSources.standard(environment, home, vault)
    }

    @When("ARTIE tries to read its credentials")
    fun tryReadStandard() {
        failure = runCatching { CredentialSources.standard(environment, home, vault) }.exceptionOrNull()
    }

    private fun writeCredentials(folder: Path, mode: String, content: String) {
        Files.createDirectories(folder)
        writeFile(folder.resolve("credentials"), mode, content)
    }

    private fun writeFile(path: Path, mode: String, content: String) {
        val file = Files.writeString(path, content)
        val bits = mode.toInt(radix = 8)
        val text = (8 downTo 0).joinToString("") { bit ->
            if (bits and (1 shl bit) == 0) "-" else "rwx"[(8 - bit) % 3].toString()
        }
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(text))
    }

    @Then("the credential for {string} is the {word} credential {string}")
    fun credentialIs(origin: String, scheme: String, value: String) =
        assertEquals(credential(scheme, value), checkNotNull(source).credentialFor(Origin.of(origin)))

    @Then("the credential for {string} is the pre-shared key of {string}")
    fun credentialIsPsk(origin: String, identity: String) {
        val credential = checkNotNull(source).credentialFor(Origin.of(origin))
        assertEquals(Credential.PreShared(identity, "s3cret-key-for-tray".toByteArray()), credential)
    }

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
