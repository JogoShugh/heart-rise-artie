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
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Validator
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.MediaTypes
import org.springframework.hateoas.mediatype.Affordances
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

/** SPEC §6.1/§6.2: Layer 1 is revalidated on every request, never stored fresh. */
private const val LAYER1_CACHE_CONTROL = "no-cache"

/** RFC 7240. An act repeats the Prefer it synced with, so it selects the same representation. */
private const val PREFER = "Prefer"

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
class GardenBedController(
    private val service: GardenBedService,
    objectMapper: ObjectMapper,
    validator: Validator,
    private val limits: ServingLimits,
) {
    private val binder = ActBodyBinder(objectMapper, validator)

    @GetMapping("/{id}")
    suspend fun getBed(
        @PathVariable id: Long,
        @RequestHeader headers: HttpHeaders,
        @RequestHeader("Prefer", required = false) prefer: String?
    ): ResponseEntity<Any> {
        val dialect = Negotiation.dialect(headers)
        val state = service.currentState(id)
        return renderState(state, dialect, prefer)
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
        @RequestHeader headers: HttpHeaders
    ): ResponseEntity<Any> {
        val dialect = Negotiation.dialect(headers)
        val instant = try {
            Instant.parse(asOf)
        } catch (e: java.time.format.DateTimeParseException) {
            throw MalformedAsOfException("'$asOf' is not a valid ISO-8601 instant, e.g. 2026-07-05T00:00:00Z")
        }
        val state = service.stateAt(id, instant)
        return renderState(state, dialect)
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

    // Act bodies arrive as raw text and are bound inside act(), after the
    // precondition and legality checks (see ActBodyBinder).

    @PostMapping("/{id}/actions/sow-seed")
    suspend fun sowSeed(
        @PathVariable id: Long,
        @RequestBody(required = false) body: String?,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader headers: HttpHeaders
    ): ResponseEntity<Any> {
        // Negotiate before acting, so a 406 never follows a write.
        val dialect = Negotiation.dialect(headers)
        val updated = act(id, ifMatch, "sowSeed", headers) {
            val request = binder.bind(body, SowSeedRequest::class.java)
            service.sowSeed(id, request.seedType, request.depthCm)
        }
        return renderAct(id, updated, dialect, headers.getFirst(PREFER))
    }

    @PostMapping("/{id}/actions/water-cell")
    suspend fun waterCell(
        @PathVariable id: Long,
        @RequestBody(required = false) body: String?,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader headers: HttpHeaders
    ): ResponseEntity<Any> {
        val dialect = Negotiation.dialect(headers)
        val updated = act(id, ifMatch, "waterCell", headers) {
            val request = binder.bind(body, WaterCellRequest::class.java)
            service.waterCell(id, request.milliliters)
        }
        return renderAct(id, updated, dialect, headers.getFirst(PREFER))
    }

    @PostMapping("/{id}/actions/harvest")
    suspend fun harvest(
        @PathVariable id: Long,
        @RequestHeader(HttpHeaders.IF_MATCH, required = false) ifMatch: String?,
        @RequestHeader headers: HttpHeaders
    ): ResponseEntity<Any> {
        val dialect = Negotiation.dialect(headers)
        val (updated, result) = act(id, ifMatch, "harvest", headers) { service.harvest(id) }
        val yieldHeaders = HttpHeaders().apply {
            add("X-Harvest-Yield-Grams", result.yieldGrams.toString())
            add("X-Harvest-Crop-Name", result.cropName)
        }
        return renderAct(id, updated, dialect, headers.getFirst(PREFER), yieldHeaders)
    }

    /**
     * An act answers with the bed's new Layer 1. Content-Location names the bed
     * (RFC 9110 §8.7), so the response is a representation of /beds/{id}, not of
     * the action URL the request was sent to.
     */
    private fun renderAct(
        id: Long,
        state: GardenBedState,
        dialect: Dialect,
        prefer: String?,
        extra: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<Any> {
        val rendered = renderState(state, dialect, prefer)
        return ResponseEntity.status(rendered.statusCode)
            .headers(rendered.headers)
            .headers(extra)
            .header(HttpHeaders.CONTENT_LOCATION, "/beds/$id")
            .body(rendered.body)
    }

    private suspend fun <T> mapConcurrencyConflict(block: suspend () -> T): T =
        try {
            block()
        } catch (e: ConcurrentAppendException) {
            throw StaleWriteConflict(e.message ?: "concurrent write conflict")
        }

    /** SPEC §6.2 evaluation order: 428, then 412, then 409, then
     *  (inside [block], via the binder) 422. The first failure wins. */
    private suspend fun <T> act(
        id: Long,
        ifMatch: String?,
        affordance: String,
        headers: HttpHeaders,
        block: suspend () -> T,
    ): T {
        checkPreconditions(id, ifMatch, affordance, headers)
        return mapConcurrencyConflict(block)
    }

    /** SPEC §6.2 precondition: an action's selected representation is its
     *  owning bed's, chosen by this request's own Accept, Accept-Profile and
     *  Prefer. If-Match is compared, whole, with that representation's
     *  current ETag: strong comparison (RFC 9110 §13.1.1). Reads state only
     *  when If-Match is present, so a 428 costs no store read. */
    private suspend fun checkPreconditions(id: Long, ifMatch: String?, affordance: String, headers: HttpHeaders) {
        val failure = if (ifMatch == null) {
            PreconditionRequiredException("$affordance on bed $id needs If-Match with the bed's current ETag")
        } else {
            val current = service.currentState(id)
            val selected = selectedETag(current, headers)
            when {
                !StateETag.matches(ifMatch, selected) -> StaleWriteConflict(
                    "If-Match $ifMatch does not match $selected, the current ETag of the representation " +
                        "these request headers select for bed $id"
                )
                affordance !in allowedAffordances(current.status) -> InvalidTransitionException(
                    "$affordance is not in bed $id's current affordance set (status ${current.status})",
                    rel = GardenProfile.reference(affordance),
                )
                else -> null
            }
        }
        failure?.let { throw it }
    }

    /** The ETag of the bed representation [headers] select, at [state]. */
    private fun selectedETag(state: GardenBedState, headers: HttpHeaders): String {
        val dialect = Negotiation.dialect(headers)
        return StateETag.of(state.lastSequenceNo, dialect.etagTag, rungFor(dialect, headers.getFirst(PREFER)))
    }

    /** HAL-FORMS is the L0 on-ramp: it ignores layering preferences. No rung
     *  above the server's cap is served (SPEC §6.11.1). */
    private fun rungFor(dialect: Dialect, prefer: String?): Rung =
        if (dialect == Dialect.JSONSCHEMA) limits.cap(Rung.fromPrefer(prefer)) else Rung.L0

    /** Spring answers a matching If-None-Match with 304 from the ETag set
     *  here, copying these headers onto it, so the 304 also says no-cache. */
    private fun renderState(state: GardenBedState, dialect: Dialect, prefer: String? = null): ResponseEntity<Any> {
        val allowed = allowedAffordances(state.status)
        val rung = rungFor(dialect, prefer)
        val etag = StateETag.of(state.lastSequenceNo, dialect.etagTag, rung)

        return when (dialect) {
            Dialect.HALFORMS -> buildHalFormsResponse(state, allowed, etag)
            Dialect.JSONSCHEMA -> buildJsonSchemaResponse(state, etag, rung)
        }
    }

    /** The hal-schema-forms (jbadeau) dialect, built from
     *  JsonSchemaDialectSupport rather than Spring HATEOAS's native
     *  machinery (which only knows how to render HAL-FORMS). The profile
     *  goes in Link rel="profile", not a media-type parameter (§6.1). */
    private fun buildJsonSchemaResponse(state: GardenBedState, etag: String, rung: Rung): ResponseEntity<Any> {
        val body = JsonSchemaDialectSupport.buildBedBody(state, rung)

        val builder = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(Dialect.JSONSCHEMA.mediaType))
            .eTag(etag)
            .header(HttpHeaders.CACHE_CONTROL, LAYER1_CACHE_CONTROL)
            .header(HttpHeaders.LINK, Negotiation.profileLinks(Dialect.JSONSCHEMA))
            // Every header that can change this body is listed, so a cache
            // never serves one rung or dialect to a client that asked for
            // another. Layer 1 only: Layers 2 and 3 never vary (SPEC §6.1).
            .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, Prefer, ${Negotiation.ACCEPT_PROFILE}")

        // Preference-Applied confirms what was actually honored (RFC 7240):
        // a client cannot always tell from the payload alone whether an
        // optional preference was applied or silently ignored.
        rung.preferenceApplied?.let { builder.header("Preference-Applied", it) }

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
            .header(HttpHeaders.CACHE_CONTROL, LAYER1_CACHE_CONTROL)
            .header(HttpHeaders.LINK, Negotiation.profileLinks(Dialect.HALFORMS))
            // HAL-FORMS is always L0 here, so Prefer cannot change it.
            .header(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, ${Negotiation.ACCEPT_PROFILE}")
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
    fun handleInvalidTransition(e: InvalidTransitionException): ResponseEntity<ProblemDetail> =
        Problems.respond(
            HttpStatus.CONFLICT, "not-offered", "The act is not offered in the resource's current state",
            e.message, listOfNotNull(e.rel?.let { "rel" to it }).toMap(),
        )

    @ExceptionHandler(NotAcceptableException::class)
    fun handleNotAcceptable(e: NotAcceptableException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("error" to (e.message ?: "not acceptable")))

    @ExceptionHandler(PreconditionRequiredException::class)
    fun handlePreconditionRequired(e: PreconditionRequiredException): ResponseEntity<ProblemDetail> =
        Problems.respond(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match is required", e.message)

    @ExceptionHandler(UnprocessableContentException::class)
    fun handleUnprocessable(e: UnprocessableContentException): ResponseEntity<ProblemDetail> =
        Problems.respond(
            HttpStatus.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema",
            e.message, mapOf("violations" to e.violations),
        )

    @ExceptionHandler(MalformedActBodyException::class)
    fun handleMalformedBody(e: MalformedActBodyException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "malformed body")))

    @ExceptionHandler(StaleWriteConflict::class)
    fun handleStaleWrite(e: StaleWriteConflict): ResponseEntity<ProblemDetail> =
        Problems.respond(HttpStatus.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed", e.message)
}
