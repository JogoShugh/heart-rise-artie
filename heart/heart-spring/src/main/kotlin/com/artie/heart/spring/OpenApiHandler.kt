package com.artie.heart.spring

import com.artie.heart.OpenApiDocument
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

/**
 * The OpenAPI description at `/openapi.json`, as the exact canonical text
 * heart-core rendered. It changes only when the server's declarations or its
 * version do, so it is revalidated by its ETag rather than cached for long.
 */
class OpenApiHandler(private val document: OpenApiDocument) {
    suspend fun serve(request: ServerRequest): ServerResponse {
        val ifNoneMatch = request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH)
        val unchanged = ifNoneMatch != null && Validators.notModified(ifNoneMatch, document.validator)
        val builder = (if (unchanged) ServerResponse.status(HttpStatus.NOT_MODIFIED) else ServerResponse.ok())
            .headers {
                it.eTag = document.validator
                it.cacheControl = "no-cache"
            }
        return if (unchanged) {
            builder.buildAndAwait()
        } else {
            builder.contentType(MediaType.parseMediaType(OpenApiDocument.MEDIA_TYPE)).bodyValueAndAwait(document.body)
        }
    }
}
