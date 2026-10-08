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
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * The service index and bed search: Fielding's "entered with no prior
 * knowledge beyond the initial URI". `GET /` offers two GET forms, in either
 * dialect:
 *
 * - HAL Schema Forms: targets are templates filled from the form's own
 *   fields, `/beds{?name}` and `/beds/{id}`.
 * - HAL-FORMS: a target cannot be templated, but a GET template puts its
 *   fields in the query string (the HTML mutate-action-URL algorithm), so
 *   both forms target `/beds`, and lookup by id is `/beds?id=`.
 *
 * `GET /beds` lists matches under the IANA `item` relation (RFC 6573), each
 * titled with the bed's label, filtered by `name` (part of the label, case
 * ignored) and `id`. Both resources are Layer 1: revalidated on every use.
 * The index ignores `Prefer`: its forms are inline at every rung.
 */
@RestController
class ServiceIndexController(private val service: GardenBedService, objectMapper: ObjectMapper) {

    private val indexBodies: Map<Dialect, String> = mapOf(
        Dialect.JSONSCHEMA to objectMapper.writeValueAsString(SCHEMA_FORMS_INDEX),
        Dialect.HALFORMS to objectMapper.writeValueAsString(HAL_FORMS_INDEX),
    )

    @GetMapping("/")
    fun index(@RequestHeader headers: HttpHeaders): ResponseEntity<String> {
        val dialect = Negotiation.dialect(headers)
        val body = indexBodies.getValue(dialect)
        return layer1(dialect).eTag("\"index-${dialect.etagTag}-${sha256Hex(body).take(ETAG_HEX)}\"").body(body)
    }

    @GetMapping("/beds")
    suspend fun search(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) id: Long?,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<Map<String, Any>> {
        val dialect = Negotiation.dialect(headers)
        val beds = service.findBeds(name, id)
        val query = listOfNotNull(
            name?.let { "name=${URLEncoder.encode(it, Charsets.UTF_8)}" },
            id?.let { "id=$it" },
        )
        val self = if (query.isEmpty()) "/beds" else "/beds?" + query.joinToString("&")
        val body = mapOf(
            "count" to beds.size,
            "_links" to mapOf(
                "self" to mapOf("href" to self),
                "item" to beds.map { mapOf("href" to "/beds/${it.id}", "title" to it.label) },
            ),
        )
        return layer1(dialect).body(body)
    }

    private fun layer1(dialect: Dialect): ResponseEntity.BodyBuilder = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(dialect.mediaType))
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(HttpHeaders.LINK, Negotiation.profileLinks(dialect))
        .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, ${Negotiation.ACCEPT_PROFILE}")

    private companion object {
        const val ETAG_HEX = 16
        const val TITLE = "Garden beds"
        const val SEARCH_TITLE = "Find beds by name"
        const val LOOKUP_TITLE = "Get a bed by its id"

        val SCHEMA_FORMS_INDEX: Map<String, Any> = mapOf(
            "title" to TITLE,
            "_links" to mapOf("self" to mapOf("href" to "/")),
            "_forms" to mapOf(
                "searchBeds" to schemaForm(
                    target = "/beds{?name}",
                    title = SEARCH_TITLE,
                    field = "name",
                    property = mapOf(
                        "type" to "string",
                        "minLength" to 1,
                        "title" to "Name",
                        "description" to "Part of the bed's name; case is ignored",
                    ),
                ),
                "getBed" to schemaForm(
                    target = "/beds/{id}",
                    title = LOOKUP_TITLE,
                    field = "id",
                    property = mapOf("type" to "integer", "minimum" to 1, "title" to "Bed id"),
                ),
            ),
        )

        val HAL_FORMS_INDEX: Map<String, Any> = mapOf(
            "title" to TITLE,
            "_links" to mapOf("self" to mapOf("href" to "/")),
            "_templates" to mapOf(
                "searchBeds" to halFormsTemplate(
                    title = SEARCH_TITLE,
                    property = mapOf(
                        "name" to "name",
                        "type" to "search",
                        "required" to true,
                        "minLength" to 1,
                        "prompt" to "Part of the bed's name; case is ignored",
                    ),
                ),
                "getBed" to halFormsTemplate(
                    title = LOOKUP_TITLE,
                    property = mapOf(
                        "name" to "id",
                        "type" to "number",
                        "required" to true,
                        "min" to 1,
                        "prompt" to "Bed id",
                    ),
                ),
            ),
        )

        fun schemaForm(target: String, title: String, field: String, property: Map<String, Any>): Map<String, Any> =
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

        /** A HAL-FORMS GET template: its properties become the query string of `/beds`. */
        fun halFormsTemplate(title: String, property: Map<String, Any>): Map<String, Any> =
            mapOf("title" to title, "method" to "GET", "target" to "/beds", "properties" to listOf(property))

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
