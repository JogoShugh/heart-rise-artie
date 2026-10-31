package com.artie.heart.http4k

import com.artie.heart.HtmlAssets
import com.artie.heart.HtmlView
import com.artie.heart.Layers
import com.artie.heart.Rung
import com.artie.heart.StateETag
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

/**
 * A resource as HTML for people (spec/PLAN-HTML-DIALECT.md), rendered by heart-core: its own validator, tagged
 * `html`, revalidated on every request, and varying by Accept alone.
 */
internal class HtmlAnswer<S, K>(private val layers: Layers<S, K>) {

    fun etag(state: S): String =
        StateETag.of(layers.type.state.versionOf(state), HtmlView.ETAG_TAG, Rung.L0, HtmlView.source(layers, state))

    /** [state]'s HTML, or 304 when the request's If-None-Match names it. */
    fun represent(state: S, request: Request): Response {
        val etag = etag(state)
        val ifNoneMatch = request.header(IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)) {
            headers(Response(Status.NOT_MODIFIED), etag)
        } else {
            respond(Response(Status.OK), state)
        }
    }

    /** [response] carrying [state]'s HTML and its validator. */
    fun respond(response: Response, state: S): Response {
        val etag = etag(state)
        return headers(response, etag).header(CONTENT_TYPE, HtmlView.MEDIA_TYPE).body(HtmlView.of(layers, state, etag))
    }

    private fun headers(response: Response, etag: String): Response =
        response.header("ETag", etag).header("Cache-Control", "no-cache").header(VARY, ACCEPT)
}

/** A page's script or stylesheet, by its published name: immutable, the same for every caller. */
internal fun htmlAsset(request: Request): Response {
    val asset = HtmlAssets.byName[request.path("name")] ?: return Response(Status.NOT_FOUND)
    return Response(Status.OK).header(CONTENT_TYPE, asset.mediaType)
        .header("Cache-Control", HtmlAssets.CACHE_CONTROL).body(asset.text)
}
