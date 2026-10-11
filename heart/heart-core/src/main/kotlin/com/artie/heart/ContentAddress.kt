package com.artie.heart

import com.apicatalog.jcs.JsonCanonicalizer
import jakarta.json.Json
import jakarta.json.JsonValue
import java.io.StringReader
import java.security.MessageDigest
import java.util.Base64

/**
 * SPEC §6.5: Layer 2 and Layer 3 resources are named by their content. A
 * body is the RFC 8785 (JCS) canonical form of its JSON, and those exact bytes
 * are hashed and served, so the address is a true content address of what a
 * client receives.
 */
object ContentAddress {
    private const val ADDRESS_HEX = 16

    /** The JCS canonical text of a plain Kotlin tree (maps, lists, strings, numbers, booleans, null). */
    fun canonical(tree: Any?): String = JsonCanonicalizer.canonize(toJson(tree))

    /** The JCS canonical text of a JSON text, for schemas written as literals. */
    fun canonicalText(json: String): String =
        Json.createReader(StringReader(json)).use { JsonCanonicalizer.canonize(it.readValue()) }

    /**
     * The resource name of a canonical [body]: the first 16 hex digits of its
     * SHA-256 digest. It is a short name, not a digest, so it carries no
     * `sha256-`; only [integrity], the full digest, is ever checked.
     */
    fun address(body: String): String = hex(digest(body)).take(ADDRESS_HEX)

    /** W3C Subresource Integrity of [body]: the full digest, never truncated (HAL-SCHEMA-FORMS-HR Rule 2). */
    fun integrity(body: String): String = "sha256-" + base64(digest(body))

    /** RFC 9530 Repr-Digest of [body], in RFC 8941 byte-sequence syntax. */
    fun reprDigest(body: String): String = "sha-256=:" + base64(digest(body)) + ":"

    private fun digest(body: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    /** A plain Kotlin tree as a Jakarta JSON-P value, the type titanium-jcs canonicalizes. */
    private fun toJson(value: Any?): JsonValue = when (value) {
        is Map<*, *> -> Json.createObjectBuilder().apply {
            value.forEach { (k, v) -> add(k as String, toJson(v)) }
        }.build()
        is List<*> -> Json.createArrayBuilder().apply { value.forEach { add(toJson(it)) } }.build()
        else -> scalar(value)
    }

    private fun scalar(value: Any?): JsonValue = when (value) {
        null -> JsonValue.NULL
        is JsonValue -> value
        is String -> Json.createValue(value)
        is Int -> Json.createValue(value)
        is Long -> Json.createValue(value)
        is Double -> Json.createValue(value)
        is Boolean -> if (value) JsonValue.TRUE else JsonValue.FALSE
        else -> throw IllegalArgumentException("Unsupported value type for JCS canonicalization: ${value::class}")
    }
}
