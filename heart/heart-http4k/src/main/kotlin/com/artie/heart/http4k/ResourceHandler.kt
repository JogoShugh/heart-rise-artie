package com.artie.heart.http4k

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.ActRequests
import com.artie.heart.ActTargets
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
import org.http4k.core.Method
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
    private val pipeline = ActPipeline(type, mapper, limits.validatorRequired)
    private val targets = ActTargets(resource.layers)
    private val html = HtmlAnswer(resource.layers)

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
        val accept = request.header(ACCEPT)
        return when {
            Negotiation.wantsHtml(accept) -> html.represent(state, request)
            Negotiation.wantsPlainJson(accept) -> plain(Response(Status.OK), state)
            else -> dialect(request).let { Rendered(state, it, limits.rung(it, request)).answer(request) }
        }
    }

    fun act(request: Request): Response {
        val act = type.acts.firstOrNull { it.answersAt(checkNotNull(request.path("path"))) }
        val plainJson = Negotiation.wantsPlainJson(request.header(ACCEPT))
        return when {
            act == null -> Response(Status.NOT_FOUND)
            // SPEC §6.3: the act's target is a resource; GET reads it, under its owner's ETag.
            request.method in READS && !plainJson -> target(act, request)
            !ActRequests.allowed(act, request.method.name) ->
                Response(Status.METHOD_NOT_ALLOWED).header(ALLOW, act.method)
            plainJson -> actPlain(act, request)
            else -> actHeart(act, request)
        }
    }

    /**
     * [act]'s target, read: 200 with its representation, under the ETag of the owner's representation this
     * request selects; 304 while that is unchanged; 409, with why, when the owner doesn't offer it now.
     */
    private fun target(act: Act, request: Request): Response {
        val dialect = dialect(request)
        val state = runBlocking { pipeline.checkOffered(act.name) { resource.locate(variables(request)) } }
        val owner = Rendered(state, dialect, limits.rung(dialect, request))
        val body = when (dialect) {
            Dialect.JSONSCHEMA -> targets.of(state, act, limits.rung(dialect, request), owner.etag)
            Dialect.HALFORMS -> targets.halForms(state, act, owner.etag)
        }
        val ifNoneMatch = request.header(IF_NONE_MATCH)
        if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, owner.etag)) {
            return owner.headers(Response(Status.NOT_MODIFIED))
        }
        val answered = owner.headers(Response(Status.OK).header(CONTENT_TYPE, dialect.mediaType))
        return if (request.method == Method.HEAD) answered else answered.body(mapper.writeValueAsString(body))
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
            pipeline.run(act.name, input(act, request), checked) { input ->
                resource.perform(checked, act.name, input)
            }
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
    private fun actPlain(act: Act, request: Request): Response {
        val variables = variables(request)
        val name = act.name
        val done = runBlocking {
            val checked = pipeline.checkOffered(name) { resource.locate(variables) }
            pipeline.run(name, input(act, request), checked) { input -> resource.perform(checked, name, input) }
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

        /** 200 with this representation, or 304 when [request]'s If-None-Match already names it. */
        fun answer(request: Request): Response {
            val ifNoneMatch = request.header(IF_NONE_MATCH)
            return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)) {
                headers(Response(Status.NOT_MODIFIED))
            } else {
                respond(Response(Status.OK))
            }
        }
    }

    /** The item path's variables, as the request's path filled them. */
    private fun variables(request: Request): Map<String, String> =
        type.itemPath.variables.mapNotNull { name -> request.path(name)?.let { name to it } }.toMap()

    private companion object {
        /** The methods that read an act's target rather than perform the act. */
        val READS = setOf(Method.GET, Method.HEAD)

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

        /** The act's input: the body, or a DELETE's query (SPEC §6.2). */
        fun input(act: Act, request: Request): String? = ActRequests.input(act, body(request), request.uri.query)

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
internal const val ALLOW = "Allow"
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
