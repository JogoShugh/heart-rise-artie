package com.artie.heart.http4k

import com.artie.heart.Act
import com.artie.heart.ActPipeline
import com.artie.heart.ActRequests
import com.artie.heart.HeartResource
import com.artie.heart.HtmlAssets
import com.artie.heart.HtmlProblem
import com.artie.heart.HtmlView
import com.artie.heart.Negotiation
import com.artie.heart.Rung
import com.artie.heart.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

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
    fun represent(state: S, request: Request): Response {
        val etag = etag(state)
        val ifNoneMatch = request.header(IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)) {
            headers(Response(Status.NOT_MODIFIED), etag)
        } else {
            respond(Response(Status.OK), state)
        }
    }

    /**
     * [act], sent by a browser: If-Match compared, whole, with the HTML's validator; the form's fields typed by the
     * act's schema; the answer the new page, or, when the act made a resource, a move to it.
     */
    fun act(act: Act, request: Request, locate: suspend () -> S): Response {
        val done = runBlocking {
            val checked = pipeline.check(act.name, request.header(IF_MATCH), locate) { current, held ->
                StateETag.matches(held, etag(current))
            }
            pipeline.run(act.name, input(act, request), checked) { input -> resource.perform(checked, act.name, input) }
        }
        val title = layers.type.profile.relation(act.relation).title
        val answered = done.headers.entries.fold(Response(Status.OK)) { r, (name, value) -> r.header(name, value) }
            .header(CONTENT_LOCATION, resource.location(done.state))
            .header(HX_TRIGGER, mapper.writeValueAsString(mapOf(DONE to "$title: done.")))
        val created = done.created
        return respond(
            if (created == null) answered else answered.status(Status.CREATED).header(LOCATION, created)
                .header(HX_REDIRECT, created),
            done.state,
        )
    }

    /** [response] carrying [state]'s HTML and its validator. */
    fun respond(response: Response, state: S): Response {
        val etag = etag(state)
        return headers(response, etag).header(CONTENT_TYPE, HtmlView.MEDIA_TYPE).body(HtmlView.of(layers, state, etag))
    }

    private fun headers(response: Response, etag: String): Response =
        response.header("ETag", etag).header("Cache-Control", "no-cache").header(VARY, ACCEPT)

    /** A DELETE's query, a browser's form fields typed by the act's schema, or a JSON body as sent. */
    private fun input(act: Act, request: Request): String? {
        val body = request.bodyString().ifEmpty { null }
        val form = request.header(CONTENT_TYPE).orEmpty().startsWith(FORM)
        return when {
            act.method == Act.DELETE -> ActRequests.input(act, null, request.uri.query)
            form -> ActRequests.fromForm(act.schema, body)
            else -> body
        }
    }

    private companion object {
        const val FORM = "application/x-www-form-urlencoded"
        const val HX_TRIGGER = "HX-Trigger"
        const val HX_REDIRECT = "HX-Redirect"
        const val DONE = "heart:done"
    }
}

/** A page's script or stylesheet, by its published name: immutable, the same for every caller. */
internal fun htmlAsset(request: Request): Response {
    val asset = HtmlAssets.byName[request.path("name")] ?: return Response(Status.NOT_FOUND)
    return Response(Status.OK).header(CONTENT_TYPE, asset.mediaType)
        .header("Cache-Control", HtmlAssets.CACHE_CONTROL).body(asset.text)
}

/** A refusal, for a browser, as HTML: the same problem, the same status, in the dialog that asked. */
internal val htmlRefusals = Filter { next ->
    { request ->
        val response = next(request)
        if (Negotiation.wantsHtml(request.header(ACCEPT)) && response.header(CONTENT_TYPE) == Refusals.PROBLEM_JSON) {
            response.replaceHeader(CONTENT_TYPE, HtmlProblem.MEDIA_TYPE).body(HtmlProblem.of(response.bodyString()))
        } else {
            response
        }
    }
}
