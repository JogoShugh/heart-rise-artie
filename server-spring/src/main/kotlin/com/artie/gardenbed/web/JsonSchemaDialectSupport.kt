package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
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
 * The three layers, per SPEC 0.4 §6.4 (see Rung):
 *
 *   GET /beds/{id}                  -- mutable, ETag-revalidated.
 *       No Prefer (L0): collapses ALL THREE layers into one response --
 *           forms AND their full schema bodies, all inline.
 *       Prefer: forms=cid (L1) or forms=cid, schema=cid (L2): state
 *           plus a link to that rung's affordance set.
 *
 *   GET /affordance-sets/sha256-{hash}  -- immutable, never negotiates.
 *       The L1 set embeds schema bodies; the L2 set carries only each
 *       schema's $id. Different bytes, so different addresses (§6.5).
 *
 *   GET /schemas/sha256-{hash}      -- immutable. A real sha256 of the
 *       schema's own canonical bytes.
 *
 * Because this domain's affordances are fully determined by
 * GardenBedStatus (a 3-value enum), there are exactly 6 possible
 * affordance-sets, ever (3 statuses x 2 renderings) -- computed once,
 * at class-init, same as the individual schemas.
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

    /** One Layer 2 resource per (status, rendering). SPEC 0.4 §6.5: the
     *  L1 set (schemas embedded) and the L2 set (schema pointers) are
     *  different bytes, so each gets its own address. Both are always
     *  templated, so every bed in one status shares them. */
    private data class SetKey(val status: GardenBedStatus, val rendering: SetRendering)

    /** JCS canonical JSON for each set: these exact bytes are hashed AND served. */
    private val setJsonByKey: Map<SetKey, String> =
        GardenBedStatus.entries.flatMap { status -> SetRendering.entries.map { SetKey(status, it) } }
            .associateWith { key ->
                val embed = key.rendering == SetRendering.EMBEDDED_SCHEMAS
                val body = mapOf("_forms" to buildForms(allowedAffordances(key.status), embed))
                String(canonicalBytes(body), Charsets.UTF_8)
            }

    private val setHashByKey: Map<SetKey, String> =
        setJsonByKey.mapValues { (_, json) -> sha256Hex(json.toByteArray(Charsets.UTF_8)).take(16) }

    /** hash (no "sha256-" prefix) -> canonical set JSON, for AffordanceSetController. */
    val affordanceSetsByHash: Map<String, String> =
        setHashByKey.entries.associate { (key, hash) -> hash to setJsonByKey.getValue(key) }

    fun affordanceSetHashFor(status: GardenBedStatus, rendering: SetRendering): String =
        setHashByKey.getValue(SetKey(status, rendering))

    fun affordanceSetJson(status: GardenBedStatus, rendering: SetRendering): String =
        setJsonByKey.getValue(SetKey(status, rendering))

    /** Only used by buildBedBody's L0 (collapse-everything)
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
     * SPEC 0.4 §6.4. L0, the default: all three layers collapsed --
     * state, forms, and full schemas inline, one hop. L1 and L2: state
     * plus a link to the affordance set rendered for that rung. `type`
     * on the link tells a client what it will get before fetching.
     */
    fun buildBedBody(state: GardenBedState, rung: Rung = Rung.L0): Map<String, Any?> {
        val bedId = state.id
        val base = mutableMapOf<String, Any?>(
            "id" to bedId,
            "label" to state.label,
            "status" to state.status.name,
            "cropName" to state.cropName,
            "growthPoints" to state.growthPoints,
            "moistureLevel" to state.moistureLevel
        )

        val rendering = rung.setRendering
        if (rendering == null) {
            base["_links"] = mapOf("self" to mapOf("href" to "/beds/$bedId"))
            base.putAll(resolvedFormsForBed(state.status, bedId, embedSchemas = true))
        } else {
            val hash = affordanceSetHashFor(state.status, rendering)
            base["_links"] = mapOf(
                "self" to mapOf("href" to "/beds/$bedId"),
                "http://api.example.org/rels/forms" to mapOf(
                    "href" to "/affordance-sets/sha256-$hash",
                    "type" to Dialect.JSONSCHEMA.mediaType,
                    // HAL's own link hint; replaces the withdrawn media-type parameter (§6.1).
                    "profile" to Dialect.JSONSCHEMA.profile
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
