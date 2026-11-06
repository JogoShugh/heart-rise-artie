package com.artie.heart.spring

import com.artie.heart.Dialect
import com.artie.heart.FieldTitle
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.HtmlList
import com.artie.heart.HtmlView
import com.artie.heart.Negotiation
import com.artie.heart.OpenApiDocument
import com.artie.heart.SERVICE_DESC
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * The bookmark and the searches over HTTP (SPEC §6.11, §6.11.1): `/` as the
 * service index in either dialect, or as JSON Home when Accept ranks it above
 * both; and `/{collection}` as a page of a search. heart-core builds the
 * bodies; this adds the Layer 1 headers. All are revalidated on every use.
 */
class HeartIndexHandler(private val index: ServiceIndex, private val limits: ServingLimits) {

    suspend fun index(request: ServerRequest): ServerResponse {
        val headers = request.headers().asHttpHeaders()
        return when {
            HttpNegotiation.wantsHtml(headers) -> HtmlView.indexValidator(index).let { etag ->
                answer(request, etag, HtmlView.MEDIA_TYPE, HtmlView.index(index, etag)) { html(it) }
            }
            HttpNegotiation.wantsJsonHome(headers) -> index.home(limits.maxRung).let { home ->
                answer(request, index.homeValidator(limits.maxRung), Negotiation.JSON_HOME, home) {
                    it.set(HttpHeaders.VARY, HttpHeaders.ACCEPT)
                    it.set(HttpHeaders.LINK, SERVICE_DESC_LINK)
                }
            }
            else -> HttpNegotiation.dialect(headers).let { dialect ->
                answer(request, index.validator(dialect), dialect.mediaType, index.body(dialect)) {
                    layer1(it, dialect)
                    // RFC 8631: the OpenAPI description, for a client that knows only this address.
                    it.set(HttpHeaders.LINK, "${HttpNegotiation.profileLinks(dialect)}, $SERVICE_DESC_LINK")
                }
            }
        }
    }

    suspend fun search(resource: HeartResource<*, *>, request: ServerRequest): ServerResponse = page(
        request,
        resource.searched(request.pathVariables()) { request.queryParam(it).orElse(null) },
        FieldTitle.of(resource.layers.type.collection),
    )

    /** The sub-collection [relation] under the resource the request's path names, as a search's page. */
    suspend fun listed(resource: HeartResource<*, *>, relation: String, request: ServerRequest): ServerResponse =
        page(request, resource.listed(relation, request.pathVariables()), FieldTitle.of(relation.substringAfter(':')))

    /** A page of [searched], as the request asks: HTML titled [title], plain JSON, or a dialect. */
    private suspend fun page(
        request: ServerRequest,
        searched: Pair<Search, List<Found>>,
        title: String,
    ): ServerResponse {
        val page = request.queryParam("page").map { it.toIntOrNull() }.orElse(null) ?: 1
        val (search, found) = searched
        val headers = request.headers().asHttpHeaders()
        val body = SearchPages.page(search, found, page, limits.pageSize)
        return when {
            HttpNegotiation.wantsHtml(headers) ->
                answer(request, HtmlList.validator(body), HtmlView.MEDIA_TYPE, HtmlList.page(body, title)) { html(it) }
            HttpNegotiation.wantsPlainJson(headers) ->
                PlainJson.respond(ServerResponse.ok(), SearchPages.plainPage(found, page, limits.pageSize))
            else -> HttpNegotiation.dialect(headers).let { dialect ->
                answer(request, SearchPages.validator(body, dialect), dialect.mediaType, body) { layer1(it, dialect) }
            }
        }
    }

    /** A page for people is revalidated as any Layer 1 resource, and varies by Accept alone. */
    private fun html(headers: HttpHeaders) {
        headers.cacheControl = "no-cache"
        headers.set(HttpHeaders.VARY, HttpHeaders.ACCEPT)
    }

    /** A 304 when If-None-Match already names [etag]; otherwise the body, with the same headers. */
    private suspend fun answer(
        request: ServerRequest,
        etag: String,
        type: String,
        body: Any,
        headers: (HttpHeaders) -> Unit,
    ): ServerResponse {
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        return if (ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)) {
            ServerResponse.status(HttpStatus.NOT_MODIFIED).headers { it.eTag = etag; headers(it) }.buildAndAwait()
        } else {
            ServerResponse.ok().contentType(MediaType.parseMediaType(type))
                .headers { it.eTag = etag; headers(it) }
                .bodyValueAndAwait(body)
        }
    }

    private fun layer1(headers: HttpHeaders, dialect: Dialect) {
        headers.cacheControl = "no-cache"
        headers.set(HttpHeaders.LINK, HttpNegotiation.profileLinks(dialect))
        headers.set(HttpHeaders.VARY, "${HttpHeaders.ACCEPT}, ${HttpNegotiation.ACCEPT_PROFILE}")
    }
}

private const val SERVICE_DESC_LINK =
    "<${OpenApiDocument.PATH}>; rel=\"$SERVICE_DESC\"; type=\"${OpenApiDocument.MEDIA_TYPE}\""

/** HTTP validators (RFC 9110 §13.1.2). */
object Validators {
    /** If-None-Match uses weak comparison; `*` matches any current representation. */
    fun notModified(ifNoneMatch: String, etag: String): Boolean =
        ifNoneMatch.trim() == "*" || ifNoneMatch.split(',').any { it.trim().removePrefix("W/") == etag }
}
