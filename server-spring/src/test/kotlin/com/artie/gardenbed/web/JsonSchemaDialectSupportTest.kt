package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * Regression coverage for the JCS canonicalization fix: SPEC.md §6.5
 * calls for SHA-256 over the RFC 8785 canonical JSON body for both Layer
 * 2 (affordance-sets) and Layer 3 (schemas). Before this fix, the hash
 * came from plain Jackson `writeValueAsString`, which was only
 * incidentally stable (fixed Map insertion order), not actually
 * canonical -- no recursive key-sorting, no JCS number normalization.
 */
class JsonSchemaDialectSupportTest {

    private fun sha256Hex16(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    @Test
    fun `every served schema is genuinely canonical JSON`() {
        for ((hash, body) in JsonSchemaDialectSupport.schemasByHash) {
            // No insignificant whitespace -- JCS strips it entirely. String
            // values may hold spaces ("Sow Seed", "How deep to sow, in
            // centimetres"), so they are removed before looking.
            val structure = body.replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), "\"\"")
            assertFalse(structure.any { it.isWhitespace() }, "schema $hash has structural whitespace: $body")
            assertFalse(body.contains("\n"), "schema $hash has a newline: $body")
            // Top-level keys must be recursively sorted (RFC 8785), not
            // in whatever order the hand-written literal declared them.
            assertTrue(
                body.startsWith("{\"properties\""),
                "schema $hash's keys aren't JCS-sorted (properties < required < title < type): $body"
            )
        }
    }

    @Test
    fun `every served schema's hash matches its own served bytes`() {
        for ((hash, body) in JsonSchemaDialectSupport.schemasByHash) {
            assertEquals(hash, sha256Hex16(body.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `every affordance-set's served JSON hashes to its own URL and is canonical`() {
        for (status in GardenBedStatus.entries) for (rendering in SetRendering.entries) {
            val hash = JsonSchemaDialectSupport.affordanceSetHashFor(status, rendering)
            val served = JsonSchemaDialectSupport.affordanceSetJson(status, rendering)
            assertEquals(served, JsonSchemaDialectSupport.affordanceSetsByHash[hash])

            assertEquals(hash, sha256Hex16(served.toByteArray(Charsets.UTF_8)))
            // JCS sorts keys: "contentType" must come before "method",
            // the reverse of the order buildForms inserts them in.
            assertTrue(
                served.indexOf("\"contentType\"") < served.indexOf("\"method\""),
                "$status affordance-set keys aren't JCS-sorted: $served"
            )
        }
    }
}
