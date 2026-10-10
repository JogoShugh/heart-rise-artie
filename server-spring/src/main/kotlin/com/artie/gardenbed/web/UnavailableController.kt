package com.artie.gardenbed.web

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * SPEC §6.12: why the acts a state leaves out are absent. One resource per
 * status, named by the digest of its bytes like the affordance sets, served
 * immutable; the link that leads here carries its integrity.
 */
@RestController
class UnavailableController {

    @GetMapping("/unavailable/sha256-{hash}")
    suspend fun unavailable(@PathVariable hash: String): ResponseEntity<String> {
        val body = JsonSchemaDialectSupport.unavailableByHash[hash] ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .header("Repr-Digest", JsonSchemaDialectSupport.reprDigestOf(body))
            .body(body)
    }
}
