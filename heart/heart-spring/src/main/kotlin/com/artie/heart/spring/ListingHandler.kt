package com.artie.heart.spring

import com.artie.heart.Listing
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * A declared [Listing] over HTTP: plain JSON for the request's query,
 * revalidated on every use like Layer 1 (no-cache, an ETag, 304).
 */
class ListingHandler(private val listing: Listing) {
    suspend fun serve(request: ServerRequest): ServerResponse {
        val body = listing.body(request.queryParams().toSingleValueMap())
        val etag = listing.validator(body)
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        val unchanged = ifNoneMatch != null && Validators.notModified(ifNoneMatch, etag)
        val builder = (if (unchanged) ServerResponse.status(HttpStatus.NOT_MODIFIED) else ServerResponse.ok())
            .eTag(etag).cacheControl(CacheControl.noCache())
        return if (unchanged) {
            builder.buildAndAwait()
        } else {
            builder.contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(body)
        }
    }
}
