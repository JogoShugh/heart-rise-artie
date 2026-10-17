package heart

import java.util.Base64

/**
 * Credentials from environment variables, named by the origin they are for.
 * The origin's host is upper-cased, every character other than A-Z and 0-9
 * becomes `_`, and the port follows: `http://localhost:8080` reads
 * `ARTIE_TOKEN_LOCALHOST_8080` (Bearer), or `ARTIE_USER_LOCALHOST_8080` and
 * `ARTIE_PASSWORD_LOCALHOST_8080` (Basic). A coaps origin reads a DTLS
 * pre-shared key from `ARTIE_PSK_IDENTITY_…` and `ARTIE_PSK_KEY_…` (base64).
 * The scheme is not in the name.
 *
 * Every variable is checked when the source is made, so a half-set or
 * ambiguous credential stops ARTIE at the start. Messages name variables,
 * never values.
 */
class EnvironmentCredentials(private val env: Map<String, String>) : CredentialSource {

    /** The variable names for one origin. */
    data class Names(
        val token: String,
        val user: String,
        val password: String,
        val pskIdentity: String,
        val pskKey: String,
    )

    init {
        val suffixes = env.keys.mapNotNull { name -> PREFIXES.firstOrNull(name::startsWith)?.let(name::removePrefix) }
        suffixes.toSet().forEach(::check)
    }

    override fun credentialFor(origin: Origin): Credential? {
        val names = namesFor(origin)
        val token = env[names.token]
        val user = env[names.user]
        val identity = env[names.pskIdentity]
        return when {
            origin.scheme == "coaps" -> identity?.let {
                Credential.PreShared(it, Base64.getDecoder().decode(env.getValue(names.pskKey)))
            }
            token != null -> Credential.Bearer(token)
            user != null -> Credential.Basic(user, env.getValue(names.password))
            else -> null
        }
    }

    private fun check(suffix: String) {
        val names = names(suffix)
        listOf(names.token, names.user, names.password, names.pskIdentity, names.pskKey)
            .filter { env[it]?.isEmpty() == true }
            .forEach { throw IllegalArgumentException("$it is empty") }
        require(names.token !in env || names.user !in env) { "$suffix has both a token and a user; set one" }
        requirePair(names.user, names.password)
        requirePair(names.pskIdentity, names.pskKey)
        env[names.pskKey]?.let { key ->
            require(runCatching { Base64.getDecoder().decode(key) }.isSuccess) { "${names.pskKey} is not base64" }
        }
    }

    private fun requirePair(first: String, second: String) {
        require(first !in env || second in env) { "$first is set, but $second is not" }
        require(second !in env || first in env) { "$second is set, but $first is not" }
    }

    companion object {
        private const val TOKEN = "ARTIE_TOKEN_"
        private const val USER = "ARTIE_USER_"
        private const val PASSWORD = "ARTIE_PASSWORD_"
        private const val PSK_IDENTITY = "ARTIE_PSK_IDENTITY_"
        private const val PSK_KEY = "ARTIE_PSK_KEY_"
        private val PREFIXES = listOf(TOKEN, USER, PASSWORD, PSK_IDENTITY, PSK_KEY)
        private val NOT_NAME = Regex("[^A-Z0-9]")

        private fun names(suffix: String) =
            Names(TOKEN + suffix, USER + suffix, PASSWORD + suffix, PSK_IDENTITY + suffix, PSK_KEY + suffix)

        /** The variables that hold [origin]'s credential. */
        fun namesFor(origin: Origin): Names = names(NOT_NAME.replace(origin.host.uppercase(), "_") + "_" + origin.port)
    }
}
