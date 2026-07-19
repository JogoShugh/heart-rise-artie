package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import java.security.MessageDigest

/**
 * REBUILT A SECOND TIME after a real, caught mistake: the first
 * correction (see README item 12) fixed `_forms` vs `_templates` and
 * restored content addressing for individual SCHEMAS -- but silently
 * collapsed the affordance-SET layer straight into the bed resource
 * itself. `GET /beds/{id}` was embedding the full per-affordance
 * descriptor (`_links.target.href`, `method`, `contentType`,
 * `schema.$id`) directly, every time, regardless of Prefer -- meaning
 * every bed in the same status re-sent identical bytes instead of
 * sharing one cached, content-addressed affordance-set resource. That
 * defeats the actual point: two beds that happen to be READY_TO_HARVEST
 * at the same time should point at the SAME `/affordance-sets/{hash}`
 * URL, fetched once, cached everywhere.
 *
 * The three layers, now actually three layers:
 *
 *   GET /beds/{id}                  -- mutable, ETag-revalidated.
 *       Default: minimal -- just a pointer to the affordance-set.
 *       Prefer: embed=schemas -> collapses ALL THREE layers into one
 *           response (the RFC 7240 "give me everything, save me the
 *           round trips" case) -- forms AND their full schema bodies,
 *           all inline.
 *
 *   GET /affordance-sets/sha256-{hash}  -- immutable. Identity (the
 *       hash) is based on WHICH affordances are in the set and what
 *       they target -- NOT on whether schemas are embedded, since
 *       embedding is a rendering choice, not a change of identity.
 *       Default: forms present, schema = {"$id": ...} (pointer).
 *       Prefer: embed=schemas -> schema bodies expanded fully inline.
 *
 *   GET /schemas/sha256-{hash}      -- immutable, unchanged from the
 *       previous correction. A real sha256 of the schema's own bytes.
 *
 * Because this domain's affordances are fully determined by
 * GardenBedStatus (a 3-value enum), there are exactly 3 possible
 * affordance-sets, ever -- computed once, at class-init, same as the
 * individual schemas.
 */
object JsonSchemaDialectSupport {

    // ---------------------------------------------------------------
    // Layer 3: individual schemas
    // ---------------------------------------------------------------

    private val canonicalSchemas: Map<String, String> = mapOf(
        "sowSeed" to """
            {
              "title": "Sow Seed",
              "type": "object",
              "properties": {
                "seedType": { "type": "string" },
                "depthCm": { "type": "number", "minimum": 1, "maximum": 10 }
              },
              "required": ["seedType"]
            }
        """.trimIndent(),

        "waterCell" to """
            {
              "title": "Water Cell",
              "type": "object",
              "properties": {
                "milliliters": { "type": "integer", "minimum": 1, "maximum": 1000 }
              },
              "required": ["milliliters"]
            }
        """.trimIndent(),

        "harvest" to """
            {
              "title": "Harvest",
              "type": "object",
              "properties": {}
            }
        """.trimIndent()
    )

    private val schemaHashes: Map<String, String> =
        canonicalSchemas.mapValues { (_, body) -> sha256Hex(body).take(16) }

    /** hash (no "sha256-" prefix) -> raw schema body, for SchemaController. */
    val schemasByHash: Map<String, String> =
        schemaHashes.entries.associate { (name, hash) -> hash to canonicalSchemas.getValue(name) }

    private fun schemaIdFor(name: String): String = "/schemas/sha256-${schemaHashes.getValue(name)}"

    // ---------------------------------------------------------------
    // Shared: which affordances exist for a given status. Single
    // source of truth -- both dialects (halforms via GardenBedController,
    // jsonschema via this object) read from the same mapping, so they
    // can never silently drift about what's currently allowed.
    // ---------------------------------------------------------------

    fun allowedAffordances(status: GardenBedStatus): List<String> = when (status) {
        GardenBedStatus.EMPTY -> listOf("sowSeed")
        GardenBedStatus.PLANTED -> listOf("waterCell")
        GardenBedStatus.READY_TO_HARVEST -> listOf("harvest")
    }

    // ---------------------------------------------------------------
    // Layer 2: affordance-sets. Precomputed for all 3 possible
    // statuses at class-init, same discipline as the schemas above.
    // ---------------------------------------------------------------

    /** Builds the `_forms` map for a given list of affordance names.
     *  Target hrefs are LEFT AS URI TEMPLATES (`/beds/{bedId}/actions/...`,
     *  `templated: true`) rather than resolved to a real bed id -- this
     *  is what makes the affordance-set resource genuinely
     *  bed-agnostic: the exact same bytes serve every bed currently in
     *  this status, real HTTP caches included, since nothing about the
     *  URL or body varies by which bed asked. The client expands
     *  `{bedId}` itself before POSTing, exactly the same as any other
     *  RFC 6570 URI Template in a HAL link. */
    private fun buildForms(names: List<String>, embedSchemas: Boolean): Map<String, Any?> =
        names.associateWith { name ->
            val id = schemaIdFor(name)
            val schema: Map<String, Any?> = if (embedSchemas) {
                (parseSchemaBody(canonicalSchemas.getValue(name)) as Map<*, *>)
                    .mapKeys { it.key as String }
                    .toMutableMap()
                    .apply { put("\$id", id) }
            } else {
                mapOf("\$id" to id)
            }

            mapOf(
                "_links" to mapOf(
                    "target" to mapOf(
                        "href" to "/beds/{bedId}/actions/${actionPath(name)}",
                        "templated" to true
                    )
                ),
                "method" to "POST",
                "contentType" to "application/json",
                "schema" to schema
            )
        }

    /** The CANONICAL (never-embedded) form of each status's affordance
     *  set -- this, and only this, is what gets hashed. Embedding is a
     *  rendering choice made later, at request time, and must never
     *  change a set's identity. */
    private val canonicalSetBodyByStatus: Map<GardenBedStatus, Map<String, Any?>> =
        GardenBedStatus.entries.associateWith { status ->
            mapOf("_forms" to buildForms(allowedAffordances(status), embedSchemas = false))
        }

    private val setHashByStatus: Map<GardenBedStatus, String> =
        canonicalSetBodyByStatus.mapValues { (_, body) -> sha256Hex(canonicalJson(body)).take(16) }

    /** hash (no "sha256-" prefix) -> status, for AffordanceSetController. */
    val statusByHash: Map<String, GardenBedStatus> =
        setHashByStatus.entries.associate { (status, hash) -> hash to status }

    fun affordanceSetHashFor(status: GardenBedStatus): String = setHashByStatus.getValue(status)

    /** The standalone /affordance-sets/{hash} resource's body -- always
     *  templated, genuinely identical bytes for every bed sharing this
     *  status. This IS what gets hashed; there is no separate
     *  "canonical" vs "served" distinction anymore, because nothing
     *  about this resource varies per bed at all. */
    fun affordanceSetBody(status: GardenBedStatus, embedSchemas: Boolean): Map<String, Any?> =
        mapOf("_forms" to buildForms(allowedAffordances(status), embedSchemas))

    /** Only used by buildBedBody's embed=true (collapse-everything)
     *  path, where the response is already specific to one bed and
     *  resolving `{bedId}` for convenience costs no sharing benefit --
     *  the standalone affordance-set resource above never calls this. */
    private fun resolvedFormsForBed(status: GardenBedStatus, bedId: Long, embedSchemas: Boolean): Map<String, Any?> {
        val forms = buildForms(allowedAffordances(status), embedSchemas)
        val resolved = forms.mapValues { (_, entry) ->
            @Suppress("UNCHECKED_CAST")
            val e = entry as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val links = e["_links"] as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val target = links["target"] as Map<String, Any?>
            val href = (target["href"] as String).replace("{bedId}", bedId.toString())
            e + ("_links" to mapOf("target" to mapOf("href" to href)))
        }
        return mapOf("_forms" to resolved)
    }

    // ---------------------------------------------------------------
    // Layer 1: the bed resource itself.
    // ---------------------------------------------------------------

    /**
     * Default: minimal. Just state plus a pointer to the affordance-set
     * -- no per-affordance detail at all. `type` on the link closes the
     * discovery gap (same fix discussed for HAL-FORMS's own §6 example
     * earlier) so a client knows what it'll get before fetching.
     *
     * `Prefer: embed=schemas` collapses all three layers into one
     * response -- forms AND fully expanded schemas, inline, no further
     * requests needed at all. This is the RFC 7240 "return=representation,
     * save me the round trips" case, just spanning two hops instead of
     * one.
     */
    fun buildBedBody(
        bedId: Long,
        label: String,
        status: String,
        cropName: String?,
        growthPoints: Int,
        moistureLevel: Int,
        gardenBedStatus: GardenBedStatus,
        embedSchemas: Boolean = false
    ): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "id" to bedId,
            "label" to label,
            "status" to status,
            "cropName" to cropName,
            "growthPoints" to growthPoints,
            "moistureLevel" to moistureLevel
        )

        if (embedSchemas) {
            base["_links"] = mapOf("self" to mapOf("href" to "/beds/$bedId"))
            base.putAll(resolvedFormsForBed(gardenBedStatus, bedId, embedSchemas = true))
        } else {
            val hash = affordanceSetHashFor(gardenBedStatus)
            base["_links"] = mapOf(
                "self" to mapOf("href" to "/beds/$bedId"),
                "http://api.example.org/rels/forms" to mapOf(
                    "href" to "/affordance-sets/sha256-$hash",
                    "type" to "application/hal+json;profile=\"$JBADEAU_PROFILE\""
                )
            )
        }

        return base
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Deterministic JSON serialization for hashing -- Jackson preserves
     *  Map insertion order, and buildForms/allowedAffordances always
     *  produce keys in the same fixed order per status, so this is
     *  stable across runs. */
    private fun canonicalJson(value: Any?): String =
        com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value)

    private fun parseSchemaBody(raw: String): Any {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        return mapper.readValue(raw, Any::class.java)
    }

    private fun actionPath(affordanceName: String): String = when (affordanceName) {
        "sowSeed" -> "sow-seed"
        "waterCell" -> "water-cell"
        "harvest" -> "harvest"
        else -> affordanceName
    }
}

/** The one negotiation constant shared by both dialects' media types. */
const val JBADEAU_PROFILE = "https://github.com/jbadeau/hal-schema-forms"
