package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedStatus
import com.apicatalog.jcs.JsonCanonicalizer
import jakarta.json.Json
import jakarta.json.JsonValue
import java.io.StringReader
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

    private val rawSchemas: Map<String, String> = mapOf(
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

    /** RFC 8785 (JCS) canonical form of each raw schema above -- per
     *  SPEC.md §6.5 this, not the hand-indented literal, is what gets
     *  hashed AND served, so the URL is a true content address of
     *  exactly the bytes SchemaController returns. */
    private val canonicalSchemas: Map<String, String> =
        rawSchemas.mapValues { (_, raw) -> String(canonicalBytesOfJsonText(raw), Charsets.UTF_8) }

    private val schemaHashes: Map<String, String> =
        canonicalSchemas.mapValues { (_, body) -> sha256Hex(body.toByteArray(Charsets.UTF_8)).take(16) }

    /** hash (no "sha256-" prefix) -> canonical schema body, for SchemaController. */
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
        canonicalSetBodyByStatus.mapValues { (_, body) -> sha256Hex(canonicalBytes(body)).take(16) }

    /** hash (no "sha256-" prefix) -> status, for AffordanceSetController. */
    val statusByHash: Map<String, GardenBedStatus> =
        setHashByStatus.entries.associate { (status, hash) -> hash to status }

    fun affordanceSetHashFor(status: GardenBedStatus): String = setHashByStatus.getValue(status)

    /** The standalone /affordance-sets/{hash} resource's body as a
     *  tree -- always templated, identical for every bed sharing this
     *  status. Callers that SERVE it must use affordanceSetJson below,
     *  not serialize this map themselves: a plain Jackson write keeps
     *  insertion order, so its bytes would not match the hash. */
    fun affordanceSetBody(status: GardenBedStatus, embedSchemas: Boolean): Map<String, Any?> =
        mapOf("_forms" to buildForms(allowedAffordances(status), embedSchemas))

    private val canonicalSetJsonByStatus: Map<GardenBedStatus, String> =
        canonicalSetBodyByStatus.mapValues { (_, body) -> String(canonicalBytes(body), Charsets.UTF_8) }

    private val embeddedSetJsonByStatus: Map<GardenBedStatus, String> =
        GardenBedStatus.entries.associateWith { status ->
            String(canonicalBytes(affordanceSetBody(status, embedSchemas = true)), Charsets.UTF_8)
        }

    /** The exact bytes to serve at /affordance-sets/{hash}, as JCS
     *  canonical JSON. The pointer form (embedSchemas = false) is
     *  byte-for-byte what was hashed, so its URL is a true content
     *  address (SPEC.md §6.5). The embedded form is a rendering of the
     *  same identity (§6.4) and shares its URL. */
    fun affordanceSetJson(status: GardenBedStatus, embedSchemas: Boolean): String =
        if (embedSchemas) embeddedSetJsonByStatus.getValue(status) else canonicalSetJsonByStatus.getValue(status)

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

    private fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Converts a plain Kotlin object graph (Map/List/String/Int/Long/
     *  Double/Boolean/null) into a Jakarta JSON-P `JsonValue` tree, since
     *  that's the type titanium-jcs's `JsonCanonicalizer` actually
     *  consumes. */
    private fun toJson(value: Any?): JsonValue = when (value) {
        null -> JsonValue.NULL
        is JsonValue -> value
        is String -> Json.createValue(value)
        is Int -> Json.createValue(value)
        is Long -> Json.createValue(value)
        is Double -> Json.createValue(value)
        is Boolean -> if (value) JsonValue.TRUE else JsonValue.FALSE
        is Map<*, *> -> {
            val b = Json.createObjectBuilder()
            for ((k, v) in value) b.add(k as String, toJson(v))
            b.build()
        }
        is List<*> -> {
            val b = Json.createArrayBuilder()
            for (v in value) b.add(toJson(v))
            b.build()
        }
        else -> throw IllegalArgumentException("Unsupported value type for JCS canonicalization: ${value::class}")
    }

    /** RFC 8785 (JCS) canonical UTF-8 bytes of a plain Kotlin object
     *  graph -- recursively sorted keys, no insignificant whitespace, per
     *  SPEC.md §6.5. This is what actually gets hashed for Layer 2
     *  (affordance-sets); replaces the old plain-Jackson `canonicalJson`,
     *  which was deterministic only because Map insertion order happened
     *  to be stable here, not because it was actually canonical. */
    private fun canonicalBytes(value: Any?): ByteArray =
        JsonCanonicalizer.canonize(toJson(value)).toByteArray(Charsets.UTF_8)

    /** RFC 8785 (JCS) canonical UTF-8 bytes of a raw JSON text string --
     *  used for Layer 3 (schemas), which arrive as hand-written literals
     *  rather than programmatically-built trees. */
    private fun canonicalBytesOfJsonText(raw: String): ByteArray =
        Json.createReader(StringReader(raw)).use { reader ->
            JsonCanonicalizer.canonize(reader.readValue()).toByteArray(Charsets.UTF_8)
        }

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
