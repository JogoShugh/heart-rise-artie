package heart

import java.net.URI
import java.util.Base64

/**
 * An origin (RFC 6454): scheme, host and port, as a credential is scoped to.
 * The host is lower-cased and a default port is written out, so
 * `https://GARDEN.test` and `https://garden.test:443` are one origin.
 */
data class Origin(val scheme: String, val host: String, val port: Int) {
    override fun toString() = "$scheme://$host:$port"

    /** True for a host that never leaves the machine. */
    val loopback: Boolean get() = host == "localhost" || host == "127.0.0.1" || host == "[::1]"

    companion object {
        private val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443, "coap" to 5683, "coaps" to 5684)

        /** The origin of [url]. Only the origin is read; a path is ignored. */
        fun of(url: String): Origin = requireNotNull(ofOrNull(url)) { "$url has no origin" }

        /** The origin of [url], or null for an address with no scheme, no host or no known port. */
        fun ofOrNull(url: String): Origin? {
            val uri = runCatching { URI(url) }.getOrNull()
            val scheme = uri?.scheme?.lowercase()
            val host = uri?.host?.lowercase()
            val port = uri?.port?.takeIf { it >= 0 } ?: DEFAULT_PORTS[scheme]
            return if (scheme != null && host != null && port != null) Origin(scheme, host, port) else null
        }
    }
}

/**
 * A credential for one origin. It prints as its scheme alone, so a credential
 * that reaches a log or an error message by mistake shows nothing; only
 * [AuthTransport] reads its value, as the Authorization header.
 */
sealed class Credential(val scheme: String) {
    /** The Authorization header's value. */
    internal abstract fun header(): String

    final override fun toString() = "$scheme(****)"

    /** RFC 7617. The user name cannot hold a colon. */
    class Basic(private val user: String, private val password: String) : Credential("Basic") {
        init {
            require(':' !in user) { "a Basic user name cannot contain ':'" }
        }

        override fun header() = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
        override fun equals(other: Any?) = other is Basic && other.user == user && other.password == password
        override fun hashCode() = user.hashCode() * 31 + password.hashCode()
    }

    /** RFC 6750. */
    class Bearer(private val token: String) : Credential("Bearer") {
        override fun header() = "Bearer $token"
        override fun equals(other: Any?) = other is Bearer && other.token == token
        override fun hashCode() = token.hashCode()
    }
}

/** Where ARTIE finds the credential for an origin. */
interface CredentialSource {
    /** The credential for [origin], or null for none. */
    fun credentialFor(origin: Origin): Credential?

    /** Asked once after a 401, in case the credential has changed since; by default, the same answer. */
    fun renewed(origin: Origin): Credential? = credentialFor(origin)
}

object CredentialSources {
    /** No credential for any origin. */
    val NONE: CredentialSource = of(emptyMap())

    /** The credentials the environment holds, by the convention of [EnvironmentCredentials]. */
    fun fromEnvironment(env: Map<String, String>): CredentialSource = EnvironmentCredentials(env)

    /** A fixed credential per origin, keyed by any URL of that origin. */
    fun of(byOrigin: Map<String, Credential>): CredentialSource {
        val held = byOrigin.mapKeys { Origin.of(it.key) }
        return object : CredentialSource {
            override fun credentialFor(origin: Origin) = held[origin]
        }
    }
}
