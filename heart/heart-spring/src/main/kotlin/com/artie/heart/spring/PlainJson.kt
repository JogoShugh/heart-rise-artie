package com.artie.heart.spring

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

/**
 * Plain JSON over HTTP, the OpenAPI twin (heart-core's Negotiation.wantsPlainJson):
 * a body with no links, forms or profile, and the state's validator, [etag],
 * when it is about a resource (spec/SPEC.md, "The OpenAPI twin keeps HTTP's
 * validators"). Only Accept chooses it, so only Accept is in Vary.
 */
object PlainJson {
    suspend fun respond(builder: ServerResponse.BodyBuilder, body: Any, etag: String? = null): ServerResponse =
        builder.contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .apply { etag?.let { header(HttpHeaders.ETAG, it) } }
            .bodyValueAndAwait(body)
}
