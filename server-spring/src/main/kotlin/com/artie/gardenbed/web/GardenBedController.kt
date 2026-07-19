package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.eventstore.ConcurrentAppendException
import com.artie.gardenbed.service.BedNotFoundException
import com.artie.gardenbed.service.GardenBedService
import com.artie.gardenbed.service.InvalidTransitionException
import com.artie.gardenbed.web.dto.GardenBedDto
import com.artie.gardenbed.web.dto.SowSeedRequest
import com.artie.gardenbed.web.dto.WaterCellRequest
import jakarta.validation.Valid
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.MediaTypes
import org.springframework.hateoas.mediatype.Affordances
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private enum class Dialect { HALFORMS, JSONSCHEMA }

private fun negotiateDialect(acceptHeader: String?): Dialect {
    val accept = acceptHeader ?: ""
    return when {
        accept.contains(JBADEAU_PROFILE) -> Dialect.JSONSCHEMA
        accept.contains("application/prs.hal-forms+json") -> Dialect.HALFORMS
        else -> Dialect.HALFORMS
    }
}

/** RFC 7240's own extension mechanism: any token is legal on Prefer,
 *  not just the two the RFC itself defines (return=minimal/
 *  representation). "embed=schemas" is our own token, same pattern
 *  PostgREST uses for its handling/timezone/count preferences. */
private fun wantsEmbeddedSchemas(preferHeader: String?): Boolean =
    preferHeader?.contains("embed=schemas") == true

/** The ETag is now the event sequence number itself, not a derived
 *  timestamp -- a more precise concurrency token than before, since
 *  it's the exact same number the event store's UNIQUE constraint
 *  enforces at the database level. "If-Match: seq-5" means "only
 *  proceed if this bed's last event is still exactly #5." */
private fun etagFor(state: GardenBedState): String = "\"seq-${state.lastSequenceNo}\""

/** Single source of truth now lives in JsonSchemaDialectSupport --
 *  delegating here keeps every existing call site in this file
 *  unchanged while guaranteeing both dialects can never drift apart
 *  about what's currently allowed. */
private fun allowedAffordances(status: GardenBedStatus): List<String> =
    JsonSchemaDialectSupport.allowedAffordances(status)

class StaleWriteConflict(message: String) : RuntimeException(message)
class MalformedAsOfException(message: String) : RuntimeException(message)

@RestController
@RequestMapping("/beds")
class GardenBedController(private val service: GardenBedService) {

    @GetMapping("/{id}")
    suspend fun getBed(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?,
        @RequestHeader("Prefer", required = false) prefer: String?
    ): ResponseEntity<Any> {
        val state = service.currentState(id)
        return renderState(state, negotiateDialect(accept), prefer)
    }

    /**
     * NEW capability, the actual point of this whole redesign: state
     * as of any past instant, not just "now." Same GardenBedState
     * shape, same rendering, just folded from a different (time-
     * bounded) slice of the event log. No separate "history API" was
     * needed to make this possible -- it falls straight out of the
     * event-sourced design.
     *
     * Example: GET /beds/2?asOf=2026-07-05T00:00:00Z
     */
    @GetMapping("/{id}", params = ["asOf"])
    suspend fun getBedAsOf(
        @PathVariable id: Long,
        @RequestParam asOf: String,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        val instant = try {
            Instant.parse(asOf)
        } catch (e: java.time.format.DateTimeParseException) {
            throw MalformedAsOfException("'$asOf' is not a valid ISO-8601 instant, e.g. 2026-07-05T00:00:00Z")
        }
        val state = service.stateAt(id, instant)
        return renderState(state, negotiateDialect(accept))
    }

    /** The raw event log for a bed -- what actually happened, in
     *  order, independent of any particular dialect's rendering. */
    @GetMapping("/{id}/history")
    suspend fun getHistory(@PathVariable id: Long): ResponseEntity<List<Map<String, Any?>>> {
        val events = service.history(id)
        val body = events.map { event ->
            mapOf(
                "sequenceNo" to event.sequenceNo,
                "type" to event::class.simpleName,
                "occurredAt" to event.occurredAt.toString(),
                "event" to event
            )
        }
        return ResponseEntity.ok(body)
    }

    @PostMapping("/{id}/actions/sow-seed")
    suspend fun sowSeed(
        @PathVariable id: Long,
        @Valid @RequestBody body: SowSeedRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        checkIfMatch(id, ifMatch)
        val updated = mapConcurrencyConflict { service.sowSeed(id, body.seedType, body.depthCm) }
        return renderState(updated, negotiateDialect(accept))
    }

    @PostMapping("/{id}/actions/water-cell")
    suspend fun waterCell(
        @PathVariable id: Long,
        @Valid @RequestBody body: WaterCellRequest,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        checkIfMatch(id, ifMatch)
        val updated = mapConcurrencyConflict { service.waterCell(id, body.milliliters) }
        return renderState(updated, negotiateDialect(accept))
    }

    @PostMapping("/{id}/actions/harvest")
    suspend fun harvest(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        checkIfMatch(id, ifMatch)
        val (updated, result) = mapConcurrencyConflict { service.harvest(id) }
        val rendered = renderState(updated, negotiateDialect(accept))
        return ResponseEntity.status(rendered.statusCode)
            .headers(rendered.headers)
            .header("X-Harvest-Yield-Grams", result.yieldGrams.toString())
            .header("X-Harvest-Crop-Name", result.cropName)
            .body(rendered.body)
    }

    private suspend fun <T> mapConcurrencyConflict(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ConcurrentAppendException) {
            throw StaleWriteConflict(e.message ?: "concurrent write conflict")
        }

    private suspend fun checkIfMatch(id: Long, ifMatch: String?) {
        if (ifMatch == null) return
        val current = service.currentState(id)
        if (etagFor(current) != ifMatch) {
            throw StaleWriteConflict(
                "If-Match $ifMatch does not match current ETag ${etagFor(current)} for bed $id"
            )
        }
    }

    private fun renderState(state: GardenBedState, dialect: Dialect, prefer: String? = null): ResponseEntity<Any> {
        val allowed = allowedAffordances(state.status)
        val etag = etagFor(state)

        return when (dialect) {
            Dialect.HALFORMS -> buildHalFormsResponse(state, allowed, etag)
            Dialect.JSONSCHEMA -> buildJsonSchemaResponse(state, allowed, etag, prefer)
        }
    }

    /** The jsonschema (jbadeau/hal-schema-forms) dialect: pointer-only,
     *  zero inline field data, built from the already-complete
     *  JsonSchemaDialectSupport rather than Spring HATEOAS's native
     *  machinery (which only knows how to render halforms). This is
     *  the piece that was designed but not connected -- now wired in. */
    private fun buildJsonSchemaResponse(
        state: GardenBedState,
        allowed: List<String>,
        etag: String,
        prefer: String?
    ): ResponseEntity<Any> {
        val embed = wantsEmbeddedSchemas(prefer)

        val body = JsonSchemaDialectSupport.buildBedBody(
            bedId = state.id,
            label = state.label,
            status = state.status.name,
            cropName = state.cropName,
            growthPoints = state.growthPoints,
            moistureLevel = state.moistureLevel,
            gardenBedStatus = state.status,
            embedSchemas = embed
        )

        val builder = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/hal+json;profile=\"$JBADEAU_PROFILE\""))
            .eTag(etag)
            // Prefer changes the response body (embedded vs. pointer-only),
            // so per RFC 7240 it MUST be listed in Vary alongside Accept --
            // otherwise a cache could serve one client's embedded response
            // to another client that never asked for it.
            .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, Prefer")

        // Preference-Applied confirms what was actually honored -- per
        // RFC 7240, necessary specifically because a client can't always
        // tell just from the payload whether an optional preference was
        // applied or silently ignored.
        if (embed) {
            builder.header("Preference-Applied", "embed=schemas")
        }

        return builder.body(body)
    }

    /**
     * Uses the official Spring HATEOAS `Affordances` API
     * (`org.springframework.hateoas.mediatype.Affordances` -- confirmed
     * via live search, not recalled from memory), but NOT the docs'
     * single-link `.afford().andAfford()....toLink()` chaining example
     * -- that shape fits multiple HTTP methods on ONE shared target
     * (e.g. PUT and PATCH on the same employee URL). It's the wrong
     * shape here: every affordance in this domain targets a genuinely
     * different URL (`/beds/{id}/actions/sow-seed` vs
     * `/beds/{id}/actions/water-cell` vs `/beds/{id}/actions/harvest`).
     * An earlier version of this file used that chaining style anyway,
     * which meant every affordance silently used `selfLink` as its
     * target -- so a client naively following the rendered HAL-FORMS
     * document would have POSTed to `/beds/{id}` itself instead of the
     * real action endpoint. Caught by actually running this and
     * inspecting real output, not by me in review.
     *
     * Fixed by building one `Affordances.of(actionLink)` per allowed
     * affordance -- each with its OWN real target link -- and
     * attaching each individually to `selfLink` via `.andAffordance()`.
     * Extracting a single `Affordance` from `.build()`'s result needed
     * a second fix too: `Affordances.build()` returns an `Affordances`
     * collection whose accessor is `.stream()` (a real `java.util.Stream`),
     * not `.first()` -- confirmed against the actual Javadoc after a
     * real compile failure (`None of the following candidates is
     * applicable: fun <T> Iterable<T>.first()...`), not guessed a
     * second time. `.stream().findFirst().orElseThrow()` is correct.
     *
     * One more thing worth knowing, NOT a bug: per the HAL-FORMS spec
     * itself (quoted earlier tonight), "if there is only one element
     * in the [_templates] collection the entry's key SHOULD be set to
     * 'default'." Every status in this domain allows exactly one
     * affordance at a time, so the rendered key will always legitimately
     * be `"default"`, never the affordance's actual name -- spec-
     * compliant behavior, not something to "fix." A real client has to
     * inspect the template's `target`/`method` to know which action
     * it's looking at, since the key alone won't say.
     */
    private fun buildHalFormsResponse(state: GardenBedState, allowed: List<String>, etag: String): ResponseEntity<Any> {
        val dto = GardenBedDto(
            id = state.id,
            label = state.label,
            status = state.status.name,
            cropName = state.cropName,
            growthPoints = state.growthPoints,
            moistureLevel = state.moistureLevel
        )

        val selfLink = Link.of("/beds/${state.id}").withSelfRel()

        // Each affordance needs its OWN target -- /beds/{id}/actions/{name},
        // not the bed's self URL. Build one Affordances.of(actionLink) per
        // allowed name, extract the single Affordance from each, and attach
        // them individually to selfLink via .andAffordance(). (The
        // single-link .afford()/.andAfford() chaining style only fits
        // multiple HTTP methods on ONE shared target -- wrong shape here,
        // since every affordance in this domain targets a different URL.)
        var linkWithAffordances = selfLink
        for (name in allowed) {
            val actionLink = Link.of("/beds/${state.id}/actions/${actionPathFor(name)}")
            val builtAffordances = Affordances.of(actionLink)
                .afford(HttpMethod.POST)
                .withInputAndOutput(inputTypeFor(name))
                .withName(name)
                .build()
            val singleAffordance = builtAffordances.stream().findFirst().orElseThrow()
            linkWithAffordances = linkWithAffordances.andAffordance(singleAffordance)
        }

        val model = EntityModel.of(dto, linkWithAffordances)

        return ResponseEntity.ok()
            .contentType(MediaTypes.HAL_FORMS_JSON)
            .eTag(etag)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .body(model)
    }

    private fun inputTypeFor(affordanceName: String): Class<*> = when (affordanceName) {
        "sowSeed" -> SowSeedRequest::class.java
        "waterCell" -> WaterCellRequest::class.java
        else -> Void::class.java
    }

    private fun actionPathFor(affordanceName: String): String = when (affordanceName) {
        "sowSeed" -> "sow-seed"
        "waterCell" -> "water-cell"
        "harvest" -> "harvest"
        else -> affordanceName
    }

    @ExceptionHandler(MalformedAsOfException::class)
    fun handleMalformedAsOf(e: MalformedAsOfException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "malformed asOf")))

    @ExceptionHandler(BedNotFoundException::class)
    fun handleNotFound(e: BedNotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "not found")))

    @ExceptionHandler(InvalidTransitionException::class)
    fun handleInvalidTransition(e: InvalidTransitionException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "invalid transition")))

    @ExceptionHandler(StaleWriteConflict::class)
    fun handleStaleWrite(e: StaleWriteConflict): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(mapOf("error" to (e.message ?: "stale write")))
}
