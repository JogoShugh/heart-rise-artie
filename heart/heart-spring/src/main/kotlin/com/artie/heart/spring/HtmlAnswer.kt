package com.artie.heart.spring

import com.artie.heart.HtmlView
import com.artie.heart.Layers
import com.artie.heart.Rung
import com.artie.heart.StateETag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * A resource as HTML for people (spec/PLAN-HTML-DIALECT.md), rendered by heart-core: its own validator, tagged
 * `html`, revalidated on every request, and varying by Accept alone.
 */
internal class HtmlAnswer<S, K>(private val layers: Layers<S, K>) {

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
}
