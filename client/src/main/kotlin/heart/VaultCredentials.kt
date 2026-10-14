package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Vault could not give the credential for [subject]; [reason] is Vault's status or reachability, never its body. */
class CredentialsUnavailable(subject: String, reason: String, cause: Throwable? = null) :
    RuntimeException("no credential for $subject: $reason", cause)

/**
 * Credentials from Vault's key/value store, version 2, read over Vault's
 * HTTP API through [transport]: the secret at `artie/<host>_<port>` under
 * [mount] holds `bearer`, or `user` and `password`. Each credential is held
 * for [ttl], then read again; [renewed] reads it again at once. The Vault
 * token goes only to Vault, in `X-Vault-Token`; [transport] is not the
 * service's, so the service's credential never goes to Vault.
 */
class VaultCredentials internal constructor(
    private val address: String,
    private val mount: String,
    private val login: VaultLogin,
    private val transport: Transport,
    private val ttl: Duration,
    private val now: () -> Instant = Instant::now,
) : CredentialSource {

    private class Held(val credential: Credential?, val until: Instant)

    private val held = ConcurrentHashMap<Origin, Held>()

    override fun credentialFor(origin: Origin): Credential? {
        val cached = held[origin]
        return if (cached != null && cached.until.isAfter(now())) cached.credential else read(origin)
    }

    override fun renewed(origin: Origin): Credential? = read(origin)

    private fun read(origin: Origin): Credential? {
        val path = "$mount/data/artie/${origin.host}_${origin.port}"
        val response = try {
            val first = get(path, login.token())
            if (first.status == FORBIDDEN && login.canRenew) get(path, login.renew()) else first
        } catch (unreachable: IOException) {
            throw CredentialsUnavailable(origin.toString(), "Vault at $address cannot be reached", unreachable)
        }
        val credential = when (response.status) {
            OK -> credential(origin, Json.parseToJsonElement(response.body).jsonObject)
            NOT_FOUND -> null
            else -> throw CredentialsUnavailable(origin.toString(), "Vault answered ${response.status} for $path")
        }
        held[origin] = Held(credential, now().plus(ttl))
        return credential
    }

    private fun get(path: String, token: String): HeartResponse =
        transport.send(HeartRequest("GET", "$address/v1/$path", mapOf(TOKEN_HEADER to token, ACCEPT)))

    private fun credential(origin: Origin, body: JsonObject): Credential {
        val data = body["data"]?.jsonObject?.get("data")?.jsonObject
            ?: throw CredentialsUnavailable(origin.toString(), "Vault's answer has no data")
        val keys = data.mapValues { (_, value) -> value.jsonPrimitive.content }
        val token = keys["bearer"]
        val user = keys["user"]
        val password = keys["password"]
        require(token == null || user == null) { "Vault's secret for $origin has both a token and a user; set one" }
        require(token != null || (user != null && password != null)) {
            "Vault's secret for $origin has neither bearer nor user and password"
        }
        return token?.let { Credential.Bearer(it) } ?: Credential.Basic(checkNotNull(user), checkNotNull(password))
    }

    companion object {
        private const val OK = 200
        private const val NOT_FOUND = 404
        private const val FORBIDDEN = 403
        private const val DEFAULT_TTL_SECONDS = 300L
        internal const val TOKEN_HEADER = "X-Vault-Token"
        internal val ACCEPT = "Accept" to "application/json"

        /**
         * The Vault source the environment asks for, or null when
         * `ARTIE_VAULT_ADDR` is not set. A Vault set up wrongly stops ARTIE
         * here, at the start.
         */
        fun fromEnvironment(env: Map<String, String>, userHome: Path, transport: Transport): VaultCredentials? {
            val address = env["ARTIE_VAULT_ADDR"]?.trimEnd('/') ?: return null
            val origin = requireNotNull(Origin.ofOrNull(address)) { "ARTIE_VAULT_ADDR is not an address" }
            require(origin.scheme != "http" || origin.loopback) {
                "ARTIE will not send a Vault token over plain HTTP to $origin"
            }
            val ttl = Duration.ofSeconds(env["ARTIE_VAULT_TTL_SECONDS"]?.toLong() ?: DEFAULT_TTL_SECONDS)
            val mount = env["ARTIE_VAULT_MOUNT"] ?: "secret"
            return VaultCredentials(address, mount, VaultLogin.from(env, userHome, address, transport), transport, ttl)
        }
    }
}

/** How ARTIE logs in to Vault: a token it is given, or an AppRole it logs in with. */
internal sealed class VaultLogin {
    abstract fun token(): String

    /** True when a refused token can be replaced by logging in again. */
    open val canRenew: Boolean = false

    /** Logs in again; only when [canRenew]. */
    open fun renew(): String = error("this Vault login cannot be renewed")

    class Given(private val token: String) : VaultLogin() {
        override fun token() = token
    }

    /**
     * Vault's AppRole: the role ID and the secret ID (read from a file only
     * its owner can read) give a token for its lease; a new one after.
     */
    class AppRole(
        private val roleId: String,
        private val secretIdFile: Path,
        private val address: String,
        private val transport: Transport,
        private val now: () -> Instant = Instant::now,
    ) : VaultLogin() {
        private var token: String? = null
        private var until: Instant = Instant.MIN

        override val canRenew = true

        @Synchronized
        override fun token(): String = token?.takeIf { until.isAfter(now()) } ?: renew()

        @Synchronized
        override fun renew(): String {
            val body = buildJsonObject {
                put("role_id", roleId)
                put("secret_id", Files.readString(secretIdFile).trim())
            }
            val response = transport.send(
                HeartRequest(
                    "POST", "$address/v1/auth/approle/login",
                    mapOf("Content-Type" to "application/json", VaultCredentials.ACCEPT), body.toString(),
                ),
            )
            if (response.status != OK) {
                throw CredentialsUnavailable("Vault", "the AppRole login was answered ${response.status}")
            }
            val auth = Json.parseToJsonElement(response.body).jsonObject.getValue("auth").jsonObject
            until = now().plusSeconds(auth.getValue("lease_duration").jsonPrimitive.long)
            return auth.getValue("client_token").jsonPrimitive.content.also { token = it }
        }

        private companion object {
            const val OK = 200
        }
    }

    companion object {
        fun from(env: Map<String, String>, userHome: Path, address: String, transport: Transport): VaultLogin {
            val role = env["ARTIE_VAULT_ROLE_ID"]
            val tokenFile = userHome.resolve(".vault-token")
            return when {
                role != null -> {
                    val file = requireNotNull(env["ARTIE_VAULT_SECRET_ID_FILE"]) {
                        "ARTIE_VAULT_ROLE_ID is set, but ARTIE_VAULT_SECRET_ID_FILE is not"
                    }
                    FileCredentials.requireOwnerOnly(Path.of(file))
                    AppRole(role, Path.of(file), address, transport)
                }
                env["VAULT_TOKEN"] != null -> Given(env.getValue("VAULT_TOKEN"))
                Files.exists(tokenFile) -> Given(Files.readString(tokenFile).trim())
                else -> throw IllegalArgumentException(
                    "ARTIE_VAULT_ADDR is set, but there is no VAULT_TOKEN, ~/.vault-token or ARTIE_VAULT_ROLE_ID",
                )
            }
        }
    }
}
