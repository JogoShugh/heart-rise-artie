package heart

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

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
        val open = Files.getPosixFilePermissions(file) - OWNER_ONLY
        require(open.isEmpty()) { "$file can be read by others; run chmod 600 on it" }
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
        require(origin.scheme != "coap" && origin.scheme != "coaps") {
            "$at: CoAP credentials are not supported (they need OSCORE or DTLS)"
        }
        require(text.substringAfter("://").substringAfter('/', missingDelimiterValue = "").isEmpty()) {
            "$at: a section names an origin, not a path"
        }
        return origin
    }

    private fun credential(origin: Origin, keys: Map<String, String>): Credential {
        val token = keys["bearer"]
        val user = keys["user"]
        val password = keys["password"]
        require(token == null || user == null) { "$origin has both a token and a user; set one" }
        require(user == null || password != null) { "$origin has a user but no password" }
        require(password == null || user != null) { "$origin has a password but no user" }
        require(listOfNotNull(token, user, password).none { it.isEmpty() }) { "$origin has an empty value" }
        return token?.let { Credential.Bearer(it) } ?: Credential.Basic(checkNotNull(user), checkNotNull(password))
    }

    private companion object {
        val KEYS = setOf("bearer", "user", "password")
        val OWNER_ONLY = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }
}
