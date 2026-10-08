package com.artie.gardenbed.web

import com.artie.gardenbed.service.GardenBedService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * The service index and bed search: Fielding's "entered with no prior
 * knowledge beyond the initial URI". `GET /` offers two GET forms whose
 * targets are filled from their own fields (HAL Schema Forms): search by
 * name, and lookup by id. `GET /beds?name=` lists matches under the IANA
 * `item` relation (RFC 6573), each titled with the bed's label.
 *
 * Both are Layer 1 resources: revalidated on every use, never shared as
 * immutable layers. The index ignores `Prefer`: its two forms are carried
 * inline at every rung. HAL Schema Forms only; HAL-FORMS cannot express a
 * target filled from a field.
 */
@RestController
class ServiceIndexController(private val service: GardenBedService, objectMapper: ObjectMapper) {

    private val indexBody: String = objectMapper.writeValueAsString(INDEX)
    private val indexETag: String = "\"index-${sha256Hex(indexBody).take(ETAG_HEX)}\""

    @GetMapping("/")
    fun index(@RequestHeader headers: HttpHeaders): ResponseEntity<String> {
        requireSchemaForms(headers)
        return hal().eTag(indexETag).body(indexBody)
    }

    @GetMapping("/beds")
    suspend fun search(
        @RequestParam(required = false) name: String?,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<Map<String, Any>> {
        requireSchemaForms(headers)
        val beds = service.findBeds(name)
        val self = if (name == null) "/beds" else "/beds?name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}"
        val body = mapOf(
            "count" to beds.size,
            "_links" to mapOf(
                "self" to mapOf("href" to self),
                "item" to beds.map { mapOf("href" to "/beds/${it.id}", "title" to it.label) },
            ),
        )
        return hal().body(body)
    }

    private fun requireSchemaForms(headers: HttpHeaders) {
        if (Negotiation.dialect(headers) != Dialect.JSONSCHEMA) {
            throw NotAcceptableException("the service index is served as ${Dialect.JSONSCHEMA.mediaType} only")
        }
    }

    private fun hal(): ResponseEntity.BodyBuilder = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(Dialect.JSONSCHEMA.mediaType))
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(HttpHeaders.LINK, Negotiation.profileLinks(Dialect.JSONSCHEMA))
        .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, ${Negotiation.ACCEPT_PROFILE}")

    private companion object {
        const val ETAG_HEX = 16

        val INDEX: Map<String, Any> = mapOf(
            "title" to "Garden beds",
            "_links" to mapOf("self" to mapOf("href" to "/")),
            "_forms" to mapOf(
                "searchBeds" to getForm(
                    target = "/beds{?name}",
                    title = "Find beds by name",
                    field = "name",
                    property = mapOf(
                        "type" to "string",
                        "minLength" to 1,
                        "title" to "Name",
                        "description" to "Part of the bed's name; case is ignored",
                    ),
                ),
                "getBed" to getForm(
                    target = "/beds/{id}",
                    title = "Get a bed by its id",
                    field = "id",
                    property = mapOf("type" to "integer", "minimum" to 1, "title" to "Bed id"),
                ),
            ),
        )

        fun getForm(target: String, title: String, field: String, property: Map<String, Any>): Map<String, Any> =
            mapOf(
                "_links" to mapOf("target" to mapOf("href" to target, "templated" to true)),
                "method" to "GET",
                "title" to title,
                "schema" to mapOf(
                    "\$schema" to "https://json-schema.org/draft/2020-12/schema",
                    "type" to "object",
                    "properties" to mapOf(field to property),
                    "required" to listOf(field),
                ),
            )

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
