package heart

/**
 * Credentials from environment variables, named by the origin they are for.
 * The origin's host is upper-cased, every character other than A-Z and 0-9
 * becomes `_`, and the port follows: `http://localhost:8080` reads
 * `ARTIE_TOKEN_LOCALHOST_8080` (Bearer), or `ARTIE_USER_LOCALHOST_8080` and
 * `ARTIE_PASSWORD_LOCALHOST_8080` (Basic). The scheme is not in the name.
 *
 * Every variable is checked when the source is made, so a half-set or
 * ambiguous credential stops ARTIE at the start. Messages name variables,
 * never values.
 */
class EnvironmentCredentials(private val env: Map<String, String>) : CredentialSource {

    /** The three variable names for one origin. */
    data class Names(val token: String, val user: String, val password: String)

    init {
        val suffixes = env.keys.mapNotNull { name -> PREFIXES.firstOrNull(name::startsWith)?.let(name::removePrefix) }
        suffixes.toSet().forEach(::check)
    }

    override fun credentialFor(origin: Origin): Credential? {
        val names = namesFor(origin)
        val token = env[names.token]
        val user = env[names.user]
        return when {
            token != null -> Credential.Bearer(token)
            user != null -> Credential.Basic(user, env.getValue(names.password))
            else -> null
        }
    }

    private fun check(suffix: String) {
        val names = Names(TOKEN + suffix, USER + suffix, PASSWORD + suffix)
        listOf(names.token, names.user, names.password).filter { env[it]?.isEmpty() == true }
            .forEach { throw IllegalArgumentException("$it is empty") }
        require(names.token !in env || names.user !in env) { "$suffix has both a token and a user; set one" }
        require(names.user !in env || names.password in env) { "${names.user} is set, but ${names.password} is not" }
        require(names.password !in env || names.user in env) { "${names.password} is set, but ${names.user} is not" }
    }

    companion object {
        private const val TOKEN = "ARTIE_TOKEN_"
        private const val USER = "ARTIE_USER_"
        private const val PASSWORD = "ARTIE_PASSWORD_"
        private val PREFIXES = listOf(TOKEN, USER, PASSWORD)
        private val NOT_NAME = Regex("[^A-Z0-9]")

        /** The variables that hold [origin]'s credential. */
        fun namesFor(origin: Origin): Names {
            val suffix = NOT_NAME.replace(origin.host.uppercase(), "_") + "_" + origin.port
            return Names(TOKEN + suffix, USER + suffix, PASSWORD + suffix)
        }
    }
}
