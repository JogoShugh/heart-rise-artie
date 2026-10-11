package com.artie.heart.spring

import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.Negotiation
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
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
        if (HttpNegotiation.wantsJsonHome(headers)) {
            val body = index.home(limits.maxRung)
            return answer(request, index.homeValidator(limits.maxRung), Negotiation.JSON_HOME, body) {
                it.set(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            }
        }
        val dialect = HttpNegotiation.dialect(headers)
        return answer(request, index.validator(dialect), dialect.mediaType, index.body(dialect)) { layer1(it, dialect) }
    }

    suspend fun search(resource: HeartResource<*, *>, request: ServerRequest): ServerResponse {
        val dialect = HttpNegotiation.dialect(request.headers().asHttpHeaders())
        val name = request.queryParam("name").orElse(null)
        val id = request.queryParam("id").map { it.toLongOrNull() }.orElse(null)
        val page = request.queryParam("page").map { it.toIntOrNull() }.orElse(null) ?: 1
        val found = checkNotNull(resource.find)(name, id)
        val search = Search(resource.layers.type.collection, name, id)
        return ServerResponse.ok().contentType(MediaType.parseMediaType(dialect.mediaType))
            .headers { layer1(it, dialect) }
            .bodyValueAndAwait(SearchPages.page(search, found, page, limits.pageSize))
    }

    /** A 304 when If-None-Match already names [etag]; otherwise the body, with the same headers. */
    private suspend fun answer(
        request: ServerRequest,
        etag: String,
        type: String,
        body: String,
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

/** HTTP validators (RFC 9110 §13.1.2). */
object Validators {
    /** If-None-Match uses weak comparison; `*` matches any current representation. */
    fun notModified(ifNoneMatch: String, etag: String): Boolean =
        ifNoneMatch.trim() == "*" || ifNoneMatch.split(',').any { it.trim().removePrefix("W/") == etag }
}
