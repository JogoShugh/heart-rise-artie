package heart

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Base64

/**
 * Credentials from a file, one origin per section:
 *
 * ```
 * [https://garden.example]
 * bearer = ...
 *
 * [http://localhost:8181]
 * user = coach
 * password = ...
 * ```
 *
 * Lines starting with `#` are comments. The file must be readable by its owner
 * only, as ssh requires of a private key. Everything is checked when the
 * source is made, so a wrong file stops ARTIE at the start. Messages name the
 * file, a line number or an origin, never a value. No file means no
 * credentials from it.
 */
class FileCredentials(private val file: Path) : CredentialSource {
    private val held: Map<Origin, Credential> = if (Files.exists(file)) read() else emptyMap()

    override fun credentialFor(origin: Origin): Credential? = held[origin]

    private fun read(): Map<Origin, Credential> {
        requireOwnerOnly(file)
        val sections = linkedMapOf<Origin, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        Files.readAllLines(file).forEachIndexed { index, raw ->
            val line = raw.trim()
            val at = "$file line ${index + 1}"
            when {
                line.isEmpty() || line.startsWith("#") -> Unit
                line.startsWith("[") && line.endsWith("]") -> {
                    val origin = origin(line.removeSurrounding("[", "]").trim(), at)
                    current = sections.getOrPut(origin) { mutableMapOf() }
                }
                else -> {
                    val (key, value) = keyAndValue(line, at)
                    checkNotNull(current) { "$at: a key before any [origin] section" }[key] = value
                }
            }
        }
        return sections.mapValues { (origin, keys) -> credential(origin, keys) }
    }

    private fun keyAndValue(line: String, at: String): Pair<String, String> {
        require('=' in line) { "$at: expected 'key = value'" }
        val key = line.substringBefore('=').trim()
        require(key in KEYS) { "$at: unknown key '$key'; use bearer, or user and password" }
        return key to line.substringAfter('=').trim()
    }

    private fun origin(text: String, at: String): Origin {
        val origin = requireNotNull(Origin.ofOrNull(text)) {
            "$at: a section names an origin, such as [https://garden.example]"
        }
        require(origin.scheme != "coap") { "$at: plain coap carries no credential; use a coaps:// section" }
        require(text.substringAfter("://").substringAfter('/', missingDelimiterValue = "").isEmpty()) {
            "$at: a section names an origin, not a path"
        }
        return origin
    }

    private fun credential(origin: Origin, keys: Map<String, String>): Credential {
        require(keys.values.none { it.isEmpty() }) { "$origin has an empty value" }
        return if (origin.scheme == "coaps") preShared(origin, keys) else httpCredential(origin, keys)
    }

    /** A coaps section: a DTLS pre-shared key, `psk_identity` and `psk_key` (base64). */
    private fun preShared(origin: Origin, keys: Map<String, String>): Credential {
        require(keys.keys.none { it in HTTP_KEYS }) { "$origin takes psk_identity and psk_key, not a bearer or a user" }
        val identity = keys["psk_identity"]
        val key = keys["psk_key"]
        require(key == null || identity != null) { "$origin has psk_key but no psk_identity" }
        require(identity != null && key != null) { "$origin has psk_identity but no psk_key" }
        val bytes = runCatching { Base64.getDecoder().decode(key) }
            .getOrElse { throw IllegalArgumentException("$origin's psk_key is not base64") }
        return Credential.PreShared(identity, bytes)
    }

    private fun httpCredential(origin: Origin, keys: Map<String, String>): Credential {
        require(keys.keys.none { it in PSK_KEYS }) {
            "$origin is not coaps; psk_identity and psk_key are for coaps sections"
        }
        val token = keys["bearer"]
        val user = keys["user"]
        val password = keys["password"]
        require(token == null || user == null) { "$origin has both a token and a user; set one" }
        require(user == null || password != null) { "$origin has a user but no password" }
        require(password == null || user != null) { "$origin has a password but no user" }
        require(listOfNotNull(token, user, password).none { it.isEmpty() }) { "$origin has an empty value" }
        return token?.let { Credential.Bearer(it) } ?: Credential.Basic(checkNotNull(user), checkNotNull(password))
    }

    internal companion object {
        private val HTTP_KEYS = setOf("bearer", "user", "password")
        private val PSK_KEYS = setOf("psk_identity", "psk_key")
        private val KEYS = HTTP_KEYS + PSK_KEYS
        private val OWNER_ONLY = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)

        /** A file holding a secret must be readable by its owner only, as ssh requires of a private key. */
        fun requireOwnerOnly(file: Path) =
            require((Files.getPosixFilePermissions(file) - OWNER_ONLY).isEmpty()) {
                "$file can be read by others; run chmod 600 on it"
            }
    }
}
