package com.artie.gardenbed.web

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Genuinely content-addressed now -- serves at /schemas/sha256-{hash},
 * where the hash is a real sha256 of the schema's own canonical bytes
 * (computed in JsonSchemaDialectSupport), not a named endpoint. This
 * restores the property named earlier tonight: the URL IS the
 * fingerprint, so `Cache-Control: immutable` is honest here in the
 * strongest sense -- the content literally cannot change without the
 * URL itself changing.
 */
@RestController
class SchemaController {

    @GetMapping("/schemas/sha256-{hash}")
    suspend fun getSchema(@PathVariable hash: String): ResponseEntity<String> {
        val schema = JsonSchemaDialectSupport.schemasByHash[hash]
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/schema+json"))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .header("Repr-Digest", JsonSchemaDialectSupport.reprDigestOf(schema))
            .body(schema)
    }
}
