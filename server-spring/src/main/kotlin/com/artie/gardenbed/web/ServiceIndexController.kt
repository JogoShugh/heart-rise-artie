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
class ServiceIndexController(
    private val service: GardenBedService,
    private val objectMapper: ObjectMapper,
    private val limits: ServingLimits,
) {

    private val indexBodies: Map<Dialect, String> = mapOf(
        Dialect.JSONSCHEMA to objectMapper.writeValueAsString(SCHEMA_FORMS_INDEX),
        Dialect.HALFORMS to objectMapper.writeValueAsString(HAL_FORMS_INDEX),
    )

    @GetMapping("/")
    fun index(@RequestHeader headers: HttpHeaders): ResponseEntity<String> {
        if (Negotiation.wantsJsonHome(headers)) return homeDocument()
        val dialect = Negotiation.dialect(headers)
        val body = indexBodies.getValue(dialect)
        return layer1(dialect).eTag("\"index-${dialect.etagTag}-${sha256Hex(body).take(ETAG_HEX)}\"").body(body)
    }

    @GetMapping("/beds")
    suspend fun search(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) id: Long?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestHeader headers: HttpHeaders,
    ): ResponseEntity<Map<String, Any>> {
        val dialect = Negotiation.dialect(headers)
        val beds = service.findBeds(name, id)
        val size = limits.pageSize
        val pageNo = page.coerceAtLeast(1)
        val lastPage = ((beds.size + size - 1) / size).coerceAtLeast(1)
        val filters = listOfNotNull(
            name?.let { "name=${URLEncoder.encode(it, Charsets.UTF_8)}" },
            id?.let { "id=$it" },
        )
        fun href(n: Int): Map<String, String> {
            val query = filters + listOfNotNull(if (n > 1) "page=$n" else null)
            return mapOf("href" to if (query.isEmpty()) "/beds" else "/beds?" + query.joinToString("&"))
        }
        // IANA link relations (RFC 8288 registry), the paging pattern of RFC 5005.
        // Page links appear only when the list does not fit on one page.
        val paging = buildMap {
            if (lastPage > 1 || pageNo > 1) {
                put("first", href(1))
                put("last", href(lastPage))
            }
            if (pageNo < lastPage) put("next", href(pageNo + 1))
            if (pageNo > 1 && pageNo <= lastPage) put("prev", href(pageNo - 1))
        }
        val items = beds.drop((pageNo - 1) * size).take(size)
        val body = mapOf(
            "count" to beds.size,
            "_links" to mapOf("self" to href(pageNo)) + paging +
                ("item" to items.map { mapOf("href" to "/beds/${it.id}", "title" to it.label) }),
        )
        return layer1(dialect).body(body)
    }

    /**
     * SPEC §6.11.1: the index as a JSON Home document (draft-nottingham-json-home).
     * Its link hints (draft-ietf-httpapi-link-hint) declare what this server
     * serves: both dialects with their profiles, the `Prefer` values honoured
     * up to [ServingLimits.maxRung] (none at L0), and that acts need If-Match.
     */
    private fun homeDocument(): ResponseEntity<String> {
        val body = objectMapper.writeValueAsString(home(limits.maxRung))
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(Negotiation.JSON_HOME))
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .eTag("\"index-home-${sha256Hex(body).take(ETAG_HEX)}\"")
            .body(body)
    }

    private fun layer1(dialect: Dialect): ResponseEntity.BodyBuilder = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(dialect.mediaType))
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header(HttpHeaders.LINK, Negotiation.profileLinks(dialect))
        .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, ${Negotiation.ACCEPT_PROFILE}")

    private companion object {
        const val ETAG_HEX = 16
        const val TITLE = "Garden beds"

        val SCHEMA_FORMS_INDEX: Map<String, Any> = mapOf(
            "title" to TITLE,
            "_links" to mapOf("self" to mapOf("href" to "/"), "curies" to GardenProfile.CURIES),
            // HAL-SCHEMA-FORMS-HR Rules 6 and 7: keyed by relation, and self-describing.
            "_forms" to mapOf(
                GardenProfile.key("searchBeds") to schemaForm(
                    target = "/beds{?name}",
                    name = "searchBeds",
                    field = "name",
                    property = mapOf(
                        "type" to "string",
                        "minLength" to 1,
                        "title" to "Name",
                        "description" to "Part of the bed's name; case is ignored",
                    ),
                ),
                GardenProfile.key("getBed") to schemaForm(
                    target = "/beds/{id}",
                    name = "getBed",
                    field = "id",
                    property = mapOf(
                        "type" to "integer",
                        "minimum" to 1,
                        "title" to "Bed id",
                        "description" to "The bed's number",
                    ),
                ),
            ),
        )

        val HAL_FORMS_INDEX: Map<String, Any> = mapOf(
            "title" to TITLE,
            "_links" to mapOf("self" to mapOf("href" to "/")),
            "_templates" to mapOf(
                "searchBeds" to halFormsTemplate(
                    title = GardenProfile.relation("searchBeds").title,
                    property = mapOf(
                        "name" to "name",
                        "type" to "search",
                        "required" to true,
                        "minLength" to 1,
                        "prompt" to "Part of the bed's name; case is ignored",
                    ),
                ),
                "getBed" to halFormsTemplate(
                    title = GardenProfile.relation("getBed").title,
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

        fun schemaForm(target: String, name: String, field: String, property: Map<String, Any>): Map<String, Any> =
            mapOf(
                "_links" to mapOf("target" to mapOf("href" to target, "templated" to true)),
                "method" to "GET",
                "title" to GardenProfile.relation(name).title,
                "description" to GardenProfile.relation(name).description,
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

        const val SPEC_URL = "https://github.com/jogoshugh/heart-rise/spec"
        const val VARS = "https://github.com/jogoshugh/heart-rise/vars"

        /** Each dialect, with its heart-rise profile in the format object's `links` (link-hint `formats`). */
        val FORMATS: Map<String, Any> =
            Dialect.entries.associate { it.mediaType to mapOf("links" to mapOf("profile" to it.profile)) }

        fun home(maxRung: Rung): Map<String, Any> {
            val acceptPrefer = maxRung.preferenceApplied?.split(",")?.map { it.trim() }
            val itemHints = buildMap<String, Any> {
                put("allow", listOf("GET"))
                put("formats", FORMATS)
                acceptPrefer?.let { put("accept-prefer", it) }
                put("precondition-req", listOf("etag"))
            }
            return mapOf(
                "api" to mapOf("title" to TITLE, "links" to mapOf("describedBy" to SPEC_URL)),
                "resources" to mapOf(
                    "item" to mapOf(
                        "hrefTemplate" to "/beds/{id}",
                        "hrefVars" to mapOf("id" to "$VARS/bed-id"),
                        "hints" to itemHints,
                    ),
                    "collection" to mapOf(
                        "hrefTemplate" to "/beds{?name,id}",
                        "hrefVars" to mapOf("name" to "$VARS/bed-name", "id" to "$VARS/bed-id"),
                        "hints" to mapOf("allow" to listOf("GET"), "formats" to FORMATS),
                    ),
                ),
            )
        }

        fun sha256Hex(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
