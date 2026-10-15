package com.artie.heart.spring

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.BadRequestException
import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.Rung
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.artie.heart.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBodyOrNull
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Layer 1 and the acts of one declared resource type, over HTTP.
 *
 * A read negotiates the dialect, reads the resource and renders it at the
 * rung asked for, capped at [ServingLimits.maxRung]. An act negotiates first,
 * so a 406 never follows a write; runs heart-core's checks (SPEC §6.2) with
 * If-Match compared, whole, against the ETag of the representation this
 * request selects; performs the act; and answers with the resource's new
 * Layer 1, naming the resource in Content-Location (RFC 9110 §8.7).
 */
class HeartResourceHandler<S, K>(
    val resource: HeartResource<S, K>,
    private val limits: ServingLimits,
    mapper: ObjectMapper,
    private val index: ServiceIndex,
) {
    private val type = resource.layers.type
    private val pipeline = ActPipeline(type, mapper)

    suspend fun read(request: ServerRequest): ServerResponse = represent(resource.load(id(request)), request)

    /** The state as of `?asOf=` (an ISO 8601 instant), rendered exactly like the current one. */
    suspend fun readAsOf(request: ServerRequest): ServerResponse {
        val asOf = request.queryParam("asOf").orElseThrow()
        val instant = try {
            Instant.parse(asOf)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("'$asOf' is not an ISO 8601 instant, such as 2026-07-05T00:00:00Z")
        }
        return represent(checkNotNull(resource.past).at(id(request), instant), request)
    }

    /** What happened to the resource, in order, whatever the dialect. */
    suspend fun history(request: ServerRequest): ServerResponse {
        val events = checkNotNull(resource.past).history(id(request)).map {
            mapOf(
                "sequenceNo" to it.sequenceNo,
                "type" to it.type,
                "occurredAt" to it.occurredAt.toString(),
                "event" to it.event,
            )
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(events)
    }

    /**
     * [state] as the representation [request] selects, answering 304 when its
     * If-None-Match already names it. A server's own routes use this to show a
     * state of their own finding, such as a past one.
     */
    suspend fun represent(state: S, request: ServerRequest): ServerResponse {
        if (HttpNegotiation.wantsPlainJson(request.headers().asHttpHeaders())) {
            return PlainJson.respond(ServerResponse.ok(), type.state.bodyOf(state))
        }
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val rendered = Rendered(state, dialect, rung(dialect, request))
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, rendered.etag)) {
            ServerResponse.status(HttpStatus.NOT_MODIFIED).headers { rendered.headers(it) }.buildAndAwait()
        } else {
            rendered.respond(ServerResponse.ok())
        }
    }

    suspend fun act(request: ServerRequest): ServerResponse {
        val act = type.acts.firstOrNull { it.answersAt(request.pathVariable("path")) }
        return when {
            act == null -> ServerResponse.notFound().buildAndAwait()
            HttpNegotiation.wantsPlainJson(request.headers().asHttpHeaders()) -> actPlain(act.name, request)
            else -> actHeart(act, request)
        }
    }

    /** An act in HEART: its validator checked first, as SPEC §6.2 says. */
    private suspend fun actHeart(act: Act, request: ServerRequest): ServerResponse {
        // Negotiate before acting, so a 406 never follows a write.
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val rung = rung(dialect, request)
        val id = id(request)
        val ifMatch = request.headers().firstHeader(HttpHeaders.IF_MATCH)
        val checked = pipeline.check(act.name, ifMatch, { resource.load(id) }) { current, held ->
            StateETag.matches(held, Rendered(current, dialect, rung).etag)
        }
        val body = request.awaitBodyOrNull<String>()
        // The act is performed on the state whose ETag matched, so a write since is stale (SPEC §6.2).
        val done = pipeline.run(act.name, body) { input -> resource.perform(checked, act.name, input) }
        return Rendered(done.state, dialect, rung).respond(
            ServerResponse.ok()
                .header(HttpHeaders.CONTENT_LOCATION, "/${type.collection}/$id")
                .headers { headers -> done.headers.forEach { (name, value) -> headers.add(name, value) } },
        )
    }

    /**
     * Act [name], which the bookmark offers, at `/actions/{path}`. Its owning
     * resource is the service index, so If-Match is compared, whole, with the
     * index's ETag in the dialect this request selects. The act brings a
     * resource into being, so the answer is 201 with that resource's Layer 1,
     * named in Location and Content-Location (RFC 9110 §15.3.2, §8.7).
     */
    suspend fun actAtBookmark(name: String, request: ServerRequest): ServerResponse {
        if (HttpNegotiation.wantsPlainJson(request.headers().asHttpHeaders())) {
            val body = request.awaitBodyOrNull<String>()
            val done = pipeline.run(name, body) { input -> checkNotNull(resource.performAtBookmark)(name, input) }
            val location = "/${type.collection}/${type.state.idOf(done.state)}"
            return PlainJson.respond(ServerResponse.created(URI.create(location)), type.state.bodyOf(done.state))
        }
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val rung = rung(dialect, request)
        pipeline.checkAtBookmark(name, request.headers().firstHeader(HttpHeaders.IF_MATCH)) { held ->
            StateETag.matches(held, index.validator(dialect))
        }
        val body = request.awaitBodyOrNull<String>()
        val done = pipeline.run(name, body) { input -> checkNotNull(resource.performAtBookmark)(name, input) }
        val location = "/${type.collection}/${type.state.idOf(done.state)}"
        return Rendered(done.state, dialect, rung).respond(
            ServerResponse.created(URI.create(location))
                .header(HttpHeaders.CONTENT_LOCATION, location)
                .headers { headers -> done.headers.forEach { (name, value) -> headers.add(name, value) } },
        )
    }

    /**
     * An act in plain JSON, the OpenAPI twin: no If-Match is asked for, so
     * the act is checked only against the state now, and refused after it
     * is sent when that state does not offer it.
     */
    private suspend fun actPlain(name: String, request: ServerRequest): ServerResponse {
        val id = id(request)
        val checked = pipeline.checkOffered(name) { resource.load(id) }
        val body = request.awaitBodyOrNull<String>()
        val done = pipeline.run(name, body) { input -> resource.perform(checked, name, input) }
        return PlainJson.respond(
            ServerResponse.ok().header(HttpHeaders.CONTENT_LOCATION, "/${type.collection}/$id"),
            type.state.bodyOf(done.state),
        )
    }

    /** One representation of one state: its validator, its headers and its body. */
    private inner class Rendered(val state: S, val dialect: Dialect, val rung: Rung) {
        /** Layer 1 in the negotiated dialect, both rendered by heart-core from the declaration. */
        private val body: Any = when (dialect) {
            Dialect.JSONSCHEMA -> resource.layers.layer1(state, rung)
            Dialect.HALFORMS -> resource.layers.halForms(state)
        }

        /** Names this representation, so a release that renders the state differently changes it. */
        val etag: String = StateETag.of(type.state.versionOf(state), dialect.etagTag, rung, body)

        /** Every header that can change the body is listed in Vary, so a cache never mixes them up. */
        fun headers(headers: HttpHeaders) {
            headers.eTag = etag
            // SPEC §6.1/§6.2: Layer 1 is revalidated on every request, never stored fresh.
            headers.cacheControl = "no-cache"
            headers.set(HttpHeaders.LINK, HttpNegotiation.profileLinks(dialect))
            headers.set(HttpHeaders.VARY, vary(dialect))
            // RFC 7240: confirms what was honoured; the payload alone may not say.
            rung.preferenceApplied?.let { headers.set(PREFERENCE_APPLIED, it) }
        }

        suspend fun respond(builder: ServerResponse.BodyBuilder): ServerResponse =
            builder.contentType(MediaType.parseMediaType(dialect.mediaType)).headers { headers(it) }
                .bodyValueAndAwait(body)
    }

    /** HAL-FORMS is the L0 on-ramp: it ignores layering preferences. No rung above the cap is served. */
    private fun rung(dialect: Dialect, request: ServerRequest): Rung =
        if (dialect == Dialect.JSONSCHEMA) {
            limits.cap(Rung.fromPrefer(request.headers().firstHeader(HttpNegotiation.PREFER)))
        } else {
            Rung.L0
        }

    private fun id(request: ServerRequest): Long =
        request.pathVariable("id").toLongOrNull()
            ?: throw com.artie.heart.ResourceNotFoundException("no ${type.collection} ${request.pathVariable("id")}")

    private companion object {
        const val PREFERENCE_APPLIED = "Preference-Applied"

        /** Layer 1 only: Layers 2 and 3 never vary (SPEC §6.1). HAL-FORMS is always L0, so Prefer cannot change it. */
        fun vary(dialect: Dialect): String = when (dialect) {
            Dialect.JSONSCHEMA -> "${HttpHeaders.ACCEPT}, ${HttpNegotiation.PREFER}, ${HttpNegotiation.ACCEPT_PROFILE}"
            Dialect.HALFORMS -> "${HttpHeaders.ACCEPT}, ${HttpNegotiation.ACCEPT_PROFILE}"
        }
    }
}
