package com.artie.heart.http4k

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.Acted
import com.artie.heart.BadRequestException
import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.Negotiation
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.Rung
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.artie.heart.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Layer 1 and the acts of one declared resource type, over HTTP, as
 * heart-spring serves them.
 *
 * A read negotiates the dialect, reads the resource and renders it at the
 * rung asked for, capped at [ServingLimits.maxRung]. An act negotiates first,
 * so a 406 never follows a write; runs heart-core's checks (SPEC §6.2) with
 * If-Match compared, whole, against the ETag of the representation this
 * request selects; performs the act; and answers with the resource's new
 * Layer 1, naming the resource in Content-Location (RFC 9110 §8.7).
 */
class ResourceHandler<S, K>(
    val resource: HeartResource<S, K>,
    private val limits: ServingLimits,
    private val mapper: ObjectMapper,
    private val index: ServiceIndex,
) {
    private val type = resource.layers.type
    private val pipeline = ActPipeline(type, mapper)

    /** The current state, or as of `?asOf=` for a type that keeps its past; any other query is not a route. */
    fun read(request: Request): Response {
        val asOf = request.query("asOf")
        val past = resource.past
        return when {
            request.uri.query.isEmpty() -> represent(runBlocking { resource.locate(variables(request)) }, request)
            asOf != null && past != null ->
                represent(runBlocking { past.at(resource.id(variables(request)), instant(asOf)) }, request)
            else -> Response(Status.NOT_FOUND)
        }
    }

    /** What happened to the resource, in order, whatever the dialect. */
    fun history(request: Request): Response {
        val events = runBlocking { checkNotNull(resource.past).history(resource.id(variables(request))) }.map {
            mapOf(
                "sequenceNo" to it.sequenceNo,
                "type" to it.type,
                "occurredAt" to it.occurredAt.toString(),
                "event" to it.event,
            )
        }
        return Response(Status.OK).header(CONTENT_TYPE, JSON).body(mapper.writeValueAsString(events))
    }

    /** [state] as the representation [request] selects, answering 304 when its If-None-Match names it. */
    fun represent(state: S, request: Request): Response {
        if (Negotiation.wantsPlainJson(request.header(ACCEPT))) return plain(Response(Status.OK), state)
        val dialect = dialect(request)
        val rendered = Rendered(state, dialect, limits.rung(dialect, request))
        val ifNoneMatch = request.header(IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, rendered.etag)) {
            rendered.headers(Response(Status.NOT_MODIFIED))
        } else {
            rendered.respond(Response(Status.OK))
        }
    }

    fun act(request: Request): Response {
        val act = type.acts.firstOrNull { it.answersAt(checkNotNull(request.path("path"))) }
        return when {
            act == null -> Response(Status.NOT_FOUND)
            Negotiation.wantsPlainJson(request.header(ACCEPT)) -> actPlain(act.name, request)
            else -> actHeart(act, request)
        }
    }

    /** An act in HEART: its validator checked first, as SPEC §6.2 says. */
    private fun actHeart(act: Act, request: Request): Response {
        // Negotiate before acting, so a 406 never follows a write.
        val dialect = dialect(request)
        val rung = limits.rung(dialect, request)
        val variables = variables(request)
        val done = runBlocking {
            val read = suspend { resource.locate(variables) }
            val checked = pipeline.check(act.name, request.header(IF_MATCH), read) { current, held ->
                StateETag.matches(held, Rendered(current, dialect, rung).etag)
            }
            // The act is performed on the state whose ETag matched, so a write since is stale (SPEC §6.2).
            pipeline.run(act.name, body(request), checked) { input -> resource.perform(checked, act.name, input) }
        }
        return Rendered(done.state, dialect, rung)
            .respond(added(Response(Status.OK).header(CONTENT_LOCATION, resource.location(done.state)), done))
    }

    /**
     * Act [name], which the bookmark offers, at `/actions/{path}`. Its owning
     * resource is the service index, so If-Match is compared, whole, with the
     * index's ETag in the dialect this request selects. The answer is 201 with
     * the new resource's Layer 1, named in Location and Content-Location.
     */
    fun actAtBookmark(name: String, request: Request): Response {
        val perform = checkNotNull(resource.performAtBookmark)
        if (Negotiation.wantsPlainJson(request.header(ACCEPT))) {
            val done = runBlocking { pipeline.run(name, body(request)) { input -> perform(name, input) } }
            val location = resource.location(done.state)
            return plain(Response(Status.CREATED).header(LOCATION, location), done.state)
        }
        val dialect = dialect(request)
        val rung = limits.rung(dialect, request)
        val done = runBlocking {
            pipeline.checkAtBookmark(name, request.header(IF_MATCH)) { held ->
                StateETag.matches(held, index.validator(dialect))
            }
            pipeline.run(name, body(request)) { input -> perform(name, input) }
        }
        val location = resource.location(done.state)
        return Rendered(done.state, dialect, rung).respond(
            added(Response(Status.CREATED).header(LOCATION, location).header(CONTENT_LOCATION, location), done),
        )
    }

    /**
     * An act in plain JSON, the OpenAPI twin: no If-Match is asked for, so
     * the act is checked only against the state now, and refused after it
     * is sent when that state does not offer it.
     */
    private fun actPlain(name: String, request: Request): Response {
        val variables = variables(request)
        val done = runBlocking {
            val checked = pipeline.checkOffered(name) { resource.locate(variables) }
            pipeline.run(name, body(request), checked) { input -> resource.perform(checked, name, input) }
        }
        val answered = added(Response(Status.OK).header(CONTENT_LOCATION, resource.location(done.state)), done)
        return plain(answered, done.state)
    }

    /** Plain JSON: a body with no links, forms, profile or validator; only Accept chooses it. */
    private fun plain(response: Response, state: S): Response =
        response.header(CONTENT_TYPE, JSON).header(VARY, ACCEPT)
            .let { if (limits.affordanceLinks) it.header(LINK, resource.layers.formsLink(state)) else it }
            .body(mapper.writeValueAsString(type.state.bodyOf(state)))

    /** One representation of one state: its validator, its headers and its body. */
    private inner class Rendered(state: S, private val dialect: Dialect, private val rung: Rung) {
        /** Layer 1 in the negotiated dialect, both rendered by heart-core from the declaration. */
        private val body: Any = when (dialect) {
            Dialect.JSONSCHEMA -> resource.layers.layer1(state, rung)
            Dialect.HALFORMS -> resource.layers.halForms(state)
        }

        /** Names this representation, so a release that renders the state differently changes it. */
        val etag: String = StateETag.of(type.state.versionOf(state), dialect.etagTag, rung, body)

        /** Every header that can change the body is listed in Vary, so a cache never mixes them up. */
        fun headers(response: Response): Response =
            response.header("ETag", etag)
                // SPEC §6.1/§6.2: Layer 1 is revalidated on every request, never stored fresh.
                .header("Cache-Control", "no-cache")
                .header(LINK, Negotiation.profileLinks(dialect))
                .header(VARY, vary(dialect))
                // RFC 7240: confirms what was honoured; the payload alone may not say.
                .let { r -> rung.preferenceApplied?.let { r.header(PREFERENCE_APPLIED, it) } ?: r }

        fun respond(response: Response): Response =
            headers(response.header(CONTENT_TYPE, dialect.mediaType)).body(mapper.writeValueAsString(body))
    }

    /** The item path's variables, as the request's path filled them. */
    private fun variables(request: Request): Map<String, String> =
        type.itemPath.variables.mapNotNull { name -> request.path(name)?.let { name to it } }.toMap()

    private companion object {
        /** HAL-FORMS is the L0 on-ramp: it ignores layering preferences. No rung above the cap is served. */
        fun ServingLimits.rung(dialect: Dialect, request: Request): Rung =
            if (dialect == Dialect.JSONSCHEMA) cap(Rung.fromPrefer(request.header(PREFER))) else Rung.L0

        fun instant(asOf: String): Instant = try {
            Instant.parse(asOf)
        } catch (_: DateTimeParseException) {
            throw BadRequestException("'$asOf' is not an ISO 8601 instant, such as 2026-07-05T00:00:00Z")
        }

        /** The act's own headers, as the domain gave them. */
        fun added(response: Response, done: Acted<*>): Response {
            val headed = done.headers.entries.fold(response) { r, (name, value) -> r.header(name, value) }
            // An act that brought another resource into being: 201, and Location names it (RFC 9110 §15.3.2).
            return done.created?.let { headed.status(Status.CREATED).header(LOCATION, it) } ?: headed
        }

        fun body(request: Request): String? = request.bodyString().ifEmpty { null }

        fun dialect(request: Request): Dialect =
            Negotiation.dialect(request.header(ACCEPT), request.header(Negotiation.ACCEPT_PROFILE))

        /** Layer 1 only: Layers 2 and 3 never vary (SPEC §6.1). HAL-FORMS is always L0, so Prefer cannot change it. */
        fun vary(dialect: Dialect): String = when (dialect) {
            Dialect.JSONSCHEMA -> "$ACCEPT, $PREFER, ${Negotiation.ACCEPT_PROFILE}"
            Dialect.HALFORMS -> "$ACCEPT, ${Negotiation.ACCEPT_PROFILE}"
        }
    }
}

internal const val ACCEPT = "Accept"
internal const val CONTENT_TYPE = "Content-Type"
internal const val CONTENT_LOCATION = "Content-Location"
internal const val IF_MATCH = "If-Match"
internal const val IF_NONE_MATCH = "If-None-Match"
internal const val JSON = "application/json"
internal const val LINK = "Link"
internal const val LOCATION = "Location"
internal const val PREFER = "Prefer"
internal const val PREFERENCE_APPLIED = "Preference-Applied"
internal const val VARY = "Vary"
