package com.artie.heart.spring

import org.springframework.security.crypto.bcrypt.BCrypt
import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * The credentials a HEART service admits: Basic users, each with a bcrypt
 * hash of its password, and Bearer tokens, each held as its SHA-256. The
 * configuration never holds a password or a token itself.
 *
 * Read from `HEART_AUTH_BASIC_USERS` (`name:bcrypt,...`) and
 * `HEART_AUTH_BEARER_TOKENS` (`sha256-hex,...`). With neither, the service
 * does not start: there is no open mode.
 */
class HeartCredentials private constructor(
    private val users: Map<String, String>,
    private val tokens: List<ByteArray>,
) {
    /** The SHA-256 of each Basic credential already checked against its bcrypt hash, so bcrypt runs once. */
    private val verified = ConcurrentHashMap.newKeySet<String>()

    /** True when [authorization], an Authorization header value, holds a credential this service admits. */
    fun admits(authorization: String?): Boolean {
        val parts = authorization?.trim()?.split(' ', limit = 2).orEmpty()
        val value = parts.getOrNull(1)?.trim().orEmpty()
        return when (parts.firstOrNull()?.lowercase()) {
            "basic" -> basic(value)
            "bearer" -> bearer(value)
            else -> false
        }
    }

    private fun basic(encoded: String): Boolean {
        val decoded = runCatching { String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) }.getOrDefault("")
        val user = decoded.substringBefore(':', missingDelimiterValue = "")
        val password = decoded.substringAfter(':', missingDelimiterValue = "")
        val key = sha256Hex("$user\u0000$password")
        if (key in verified) return true
        // An unknown user is checked against a dummy hash, so it takes as long as a known one.
        val matches = BCrypt.checkpw(password, users[user] ?: DUMMY_HASH)
        return (matches && user in users).also { admitted -> if (admitted) verified.add(key) }
    }

    private fun bearer(token: String): Boolean {
        val digest = sha256(token)
        return token.isNotEmpty() && tokens.fold(false) { found, held -> MessageDigest.isEqual(held, digest) || found }
    }

    companion object {
        private val BCRYPT = Regex("""^\$2[aby]?\$\d{2}\$[./A-Za-z0-9]{53}$""")
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
        private val DUMMY_HASH = BCrypt.hashpw("", BCrypt.gensalt())

        /** Reads the configured users and tokens, or refuses to, naming what is wrong and never a value. */
        fun parse(users: String, tokens: String): HeartCredentials {
            val userList = users.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val tokenList = tokens.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            check(userList.isNotEmpty() || tokenList.isNotEmpty()) {
                "set HEART_AUTH_BASIC_USERS or HEART_AUTH_BEARER_TOKENS; a HEART service will not start open"
            }
            val byName = userList.associate { entry ->
                val name = entry.substringBefore(':')
                val hash = entry.substringAfter(':', missingDelimiterValue = "")
                check(BCRYPT.matches(hash)) { "the user $name has no bcrypt hash" }
                name to hash
            }
            tokenList.forEach {
                check(SHA256_HEX.matches(it)) { "a Bearer token is configured as itself, not as its SHA-256 in hex" }
            }
            return HeartCredentials(byName, tokenList.map { HexFormat.of().parseHex(it) })
        }

        private fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        private fun sha256Hex(text: String): String = HexFormat.of().formatHex(sha256(text))
    }
}
