package com.artie.heart.spring

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.Profile
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Layers 2 and 3 and the absence explanations (SPEC §6.5, §6.12): one address,
 * one representation, served as the exact canonical string its address names
 * (never a map for Jackson to rewrite), immutable. These never read Prefer and
 * never send Vary: each rendering has its own address. Targets inside a set
 * stay URI templates, so the same bytes serve every resource with one key.
 */
@RestController
class HeartContentController(private val registry: HeartRegistry) {

    @GetMapping("/affordance-sets/{address}")
    fun affordanceSet(@PathVariable address: String): ResponseEntity<String> =
        immutable(registry.affordanceSets[address], Dialect.JSONSCHEMA.mediaType)

    @GetMapping("/schemas/{address}")
    fun schema(@PathVariable address: String): ResponseEntity<String> =
        immutable(registry.schemas[address], SCHEMA_JSON)

    @GetMapping("/unavailable/{address}")
    fun explanation(@PathVariable address: String): ResponseEntity<String> =
        immutable(registry.explanations[address], MediaType.APPLICATION_JSON_VALUE)

    private fun immutable(body: String?, type: String): ResponseEntity<String> =
        if (body == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CACHE_CONTROL, IMMUTABLE)
                .header(REPR_DIGEST, ContentAddress.reprDigest(body))
                .body(body)
        }

    companion object {
        const val SCHEMA_JSON = "application/schema+json"
        const val IMMUTABLE = "public, max-age=31536000, immutable"
        const val REPR_DIGEST = "Repr-Digest"
    }
}

/**
 * Each declared ALPS profile (SPEC §8.5), at `/profiles/{name}`. A relation
 * `{name}:<id>` expands to a descriptor here. A client fetches it only when
 * it wants more than a form's inline text, and may cache it for a day; the
 * ETag lets it revalidate cheaply.
 */
@RestController
class HeartProfileController(registry: HeartRegistry) {
    private val documents: Map<String, String> =
        registry.profiles.mapValues { (_, profile) -> ObjectMapper().writeValueAsString(profile.alps) }

    @GetMapping("/profiles/{name}")
    fun profile(@PathVariable name: String): ResponseEntity<String> {
        val body = documents[name] ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(Profile.ALPS_MEDIA_TYPE))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .eTag("\"alps-${ContentAddress.address(body).removePrefix("sha256-")}\"")
            .body(body)
    }
}
