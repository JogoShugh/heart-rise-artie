package com.artie.heart.spring

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

/**
 * Plain JSON over HTTP, the OpenAPI twin (heart-core's Negotiation.wantsPlainJson):
 * a body with no links, forms, profile or validator. Only Accept chooses it,
 * so only Accept is in Vary.
 */
object PlainJson {
    suspend fun respond(builder: ServerResponse.BodyBuilder, body: Any): ServerResponse =
        builder.contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT)
            .bodyValueAndAwait(body)
}
