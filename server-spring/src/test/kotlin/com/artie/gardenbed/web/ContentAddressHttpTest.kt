package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient
import java.security.MessageDigest

/**
 * SPEC.md §6.5: a content address identifies "the actual bytes" of a
 * Layer 2 or Layer 3 resource. This checks that over real HTTP: fetch
 * each content-addressed URL and hash the response body exactly as it
 * came off the wire. Nothing here recomputes the hash the way the
 * production code does, so a mismatch between what is hashed and what
 * is served cannot hide behind a shared helper.
 */
@WebFluxTest(controllers = [SchemaController::class, AffordanceSetController::class])
class ContentAddressHttpTest {

    @Autowired
    lateinit var client: WebTestClient

    private fun sha256Hex16(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }.take(16)

    private fun fetchBytes(path: String, prefer: String? = null): ByteArray {
        val body = client.get().uri(path)
            .headers { h -> if (prefer != null) h.set("Prefer", prefer) }
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Cache-Control", "public, max-age=31536000, immutable")
            .expectBody().returnResult().responseBody
        assertNotNull(body, "empty body for $path")
        return body!!
    }

    @Test
    fun `each schema URL is the sha256 of the bytes served at it`() {
        for (hash in JsonSchemaDialectSupport.schemasByHash.keys) {
            val served = fetchBytes("/schemas/sha256-$hash")
            assertEquals(hash, sha256Hex16(served), "served schema bytes don't hash to their own URL")
        }
    }

    @Test
    fun `each affordance-set URL is the sha256 of the bytes served at it`() {
        for (status in GardenBedStatus.entries) {
            val hash = JsonSchemaDialectSupport.affordanceSetHashFor(status)
            val served = fetchBytes("/affordance-sets/sha256-$hash")
            assertEquals(
                hash, sha256Hex16(served),
                "$status affordance-set bytes don't hash to their own URL: ${String(served)}"
            )
        }
    }

    @Test
    fun `embedding schemas changes the rendering but not the URL`() {
        val hash = JsonSchemaDialectSupport.affordanceSetHashFor(GardenBedStatus.EMPTY)
        val plain = String(fetchBytes("/affordance-sets/sha256-$hash"))
        val embedded = String(fetchBytes("/affordance-sets/sha256-$hash", prefer = "embed=schemas"))

        // Pointer form carries only the schema's $id; embedded form also
        // carries the schema body. Same URL serves both (§6.4, §6.5).
        assert(!plain.contains("\"properties\"")) { "plain form should not inline schema bodies: $plain" }
        assert(embedded.contains("\"properties\"")) { "embedded form should inline schema bodies: $embedded" }
    }
}
