package com.artie.heart.spring

import com.artie.heart.ActPipeline
import com.artie.heart.Dialect
import com.artie.heart.Rung
import com.artie.heart.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.mediatype.Affordances
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBodyOrNull
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

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
) {
    private val type = resource.layers.type
    private val pipeline = ActPipeline(type, mapper)

    suspend fun read(request: ServerRequest): ServerResponse = represent(resource.load(id(request)), request)

    /**
     * [state] as the representation [request] selects, answering 304 when its
     * If-None-Match already names it. A server's own routes use this to show a
     * state of their own finding, such as a past one.
     */
    suspend fun represent(state: S, request: ServerRequest): ServerResponse {
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val rendered = Rendered(state, dialect, rung(dialect, request))
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        return if (ifNoneMatch != null && notModified(ifNoneMatch, rendered.etag)) {
            ServerResponse.status(HttpStatus.NOT_MODIFIED).headers { rendered.headers(it) }.buildAndAwait()
        } else {
            rendered.respond(ServerResponse.ok())
        }
    }

    suspend fun act(request: ServerRequest): ServerResponse {
        val act = type.acts.firstOrNull { it.path == request.pathVariable("path") }
            ?: return ServerResponse.notFound().buildAndAwait()
        // Negotiate before acting, so a 406 never follows a write.
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val rung = rung(dialect, request)
        val id = id(request)
        val ifMatch = request.headers().firstHeader(HttpHeaders.IF_MATCH)
        pipeline.check(act.name, ifMatch, { resource.load(id) }) { current, held ->
            StateETag.matches(held, Rendered(current, dialect, rung).etag)
        }
        val body = request.awaitBodyOrNull<String>()
        val done = pipeline.run(act.name, body) { input -> resource.perform(id, act.name, input) }
        return Rendered(done.state, dialect, rung).respond(
            ServerResponse.ok()
                .header(HttpHeaders.CONTENT_LOCATION, "/${type.collection}/$id")
                .headers { headers -> done.headers.forEach { (name, value) -> headers.add(name, value) } },
        )
    }

    /** One representation of one state: its validator, its headers and its body. */
    private inner class Rendered(val state: S, val dialect: Dialect, val rung: Rung) {
        val etag: String = StateETag.of(type.state.versionOf(state), dialect.etagTag, rung)

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
                .bodyValueAndAwait(body())

        private fun body(): Any = when (dialect) {
            Dialect.JSONSCHEMA -> resource.layers.layer1(state, rung)
            Dialect.HALFORMS -> halForms(state)
        }
    }

    /**
     * HAL-FORMS, rendered by Spring HATEOAS: one affordance per offered act,
     * each with its own target (`/{collection}/{id}/actions/{path}`) and its
     * properties read from the act's input class. HAL-FORMS keys a single
     * template `default`; a client tells acts apart by target.
     */
    private fun halForms(state: S): EntityModel<Map<String, Any?>> {
        val id = type.state.idOf(state)
        var self = Link.of("/${type.collection}/$id").withSelfRel()
        for (name in resource.layers.offered(type.state.keyOf(state))) {
            val act = type.act(name)
            val affordance = Affordances.of(Link.of("/${type.collection}/$id/actions/${act.path}"))
                .afford(HttpMethod.POST)
                .withInputAndOutput(act.input ?: Void::class.java)
                .withName(name)
                .build()
                .stream().findFirst().orElseThrow()
            self = self.andAffordance(affordance)
        }
        return EntityModel.of(type.state.bodyOf(state), self)
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

        /** RFC 9110 §13.1.2: If-None-Match uses weak comparison; `*` matches any current representation. */
        fun notModified(ifNoneMatch: String, etag: String): Boolean =
            ifNoneMatch.trim() == "*" || ifNoneMatch.split(',').any { it.trim().removePrefix("W/") == etag }
    }
}
