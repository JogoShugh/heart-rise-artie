package com.artie.heart.spring

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.ActRequests
import com.artie.heart.HeartResource
import com.artie.heart.HtmlView
import com.artie.heart.Rung
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

/**
 * A resource as HTML for people (spec/PLAN-HTML-DIALECT.md), rendered by heart-core: its own validator, tagged
 * `html`, revalidated on every request, and varying by Accept alone; and its acts as a browser sends them, form
 * fields under that validator, answered with the new page and a line that says what was done.
 */
internal class HtmlAnswer<S, K>(
    private val resource: HeartResource<S, K>,
    private val pipeline: ActPipeline<S, K>,
    private val mapper: ObjectMapper,
) {
    private val layers = resource.layers

    fun etag(state: S): String =
        StateETag.of(layers.type.state.versionOf(state), HtmlView.ETAG_TAG, Rung.L0, HtmlView.source(layers, state))

    /** [state]'s HTML, or 304 when the request's If-None-Match names it. */
    suspend fun represent(state: S, request: ServerRequest): ServerResponse {
        val etag = etag(state)
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)) {
            ServerResponse.status(HttpStatus.NOT_MODIFIED).headers { headers(it, etag) }.buildAndAwait()
        } else {
            respond(ServerResponse.ok(), state)
        }
    }

    /**
     * [act], sent by a browser: If-Match compared, whole, with the HTML's validator; the form's fields typed by the
     * act's schema; the answer the new page, or, when the act made a resource, a move to it.
     */
    suspend fun act(act: Act, request: ServerRequest, locate: suspend () -> S): ServerResponse {
        val ifMatch = request.headers().firstHeader(HttpHeaders.IF_MATCH)
        val checked = pipeline.check(act.name, ifMatch, locate) { current, held ->
            StateETag.matches(held, etag(current))
        }
        val input = input(act, request)
        val done = pipeline.run(act.name, input, checked) { given -> resource.perform(checked, act.name, given) }
        val title = layers.type.profile.relation(act.relation).title
        val created = done.created
        val builder = (if (created == null) ServerResponse.ok() else ServerResponse.created(URI.create(created)))
            .header(HttpHeaders.CONTENT_LOCATION, resource.location(done.state))
            .header(HX_TRIGGER, mapper.writeValueAsString(mapOf(DONE to "$title: done.")))
            .headers { headers -> done.headers.forEach { (name, value) -> headers.add(name, value) } }
        created?.let { builder.header(HX_REDIRECT, it) }
        return respond(builder, done.state)
    }

    /** [builder] answering with [state]'s HTML and its validator. */
    suspend fun respond(builder: ServerResponse.BodyBuilder, state: S): ServerResponse {
        val etag = etag(state)
        return builder.contentType(MediaType.parseMediaType(HtmlView.MEDIA_TYPE)).headers { headers(it, etag) }
            .bodyValueAndAwait(HtmlView.of(layers, state, etag))
    }

    private fun headers(headers: HttpHeaders, etag: String) {
        headers.eTag = etag
        headers.cacheControl = "no-cache"
        headers.set(HttpHeaders.VARY, HttpHeaders.ACCEPT)
    }

    /** A DELETE's query, a browser's form fields typed by the act's schema, or a JSON body as sent. */
    private suspend fun input(act: Act, request: ServerRequest): String? {
        if (act.method == Act.DELETE) return ActRequests.input(act, null, request.uri().rawQuery)
        val body = request.awaitBodyOrNull<String>()
        val form = request.headers().contentType().map { it.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED) }
        return if (form.orElse(false)) ActRequests.fromForm(act.schema, body) else body
    }

    private companion object {
        const val HX_TRIGGER = "HX-Trigger"
        const val HX_REDIRECT = "HX-Redirect"
        const val DONE = "heart:done"
    }
}
