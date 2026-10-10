package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.SetRendering
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

    private fun fetchBytes(path: String): ByteArray {
        val body = client.get().uri(path)
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
        for (status in GardenBedStatus.entries) for (rendering in SetRendering.entries) {
            val hash = JsonSchemaDialectSupport.affordanceSetHashFor(status, rendering)
            val served = fetchBytes("/affordance-sets/sha256-$hash")
            assertEquals(
                hash, sha256Hex16(served),
                "$status/$rendering affordance-set bytes don't hash to their own URL: ${String(served)}"
            )
        }
    }

    @Test
    fun `L1 and L2 renderings are different bytes at different URLs`() {
        val l1 = JsonSchemaDialectSupport.affordanceSetHashFor(GardenBedStatus.EMPTY, SetRendering.EMBEDDED_SCHEMAS)
        val l2 = JsonSchemaDialectSupport.affordanceSetHashFor(GardenBedStatus.EMPTY, SetRendering.SCHEMA_POINTERS)
        val embedded = String(fetchBytes("/affordance-sets/sha256-$l1"))
        val pointers = String(fetchBytes("/affordance-sets/sha256-$l2"))

        // SPEC 0.4 §6.5: one address, one representation. The L1 set inlines
        // schema bodies; the L2 set carries only each schema's $id.
        assert(l1 != l2) { "L1 and L2 sets share address $l1" }
        assert(embedded.contains("\"properties\"")) { "L1 set should inline schema bodies: $embedded" }
        assert(!pointers.contains("\"properties\"")) { "L2 set should not inline schema bodies: $pointers" }
    }
}
