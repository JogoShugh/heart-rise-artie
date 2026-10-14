package com.artie.heart.spring

import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.PROBLEM_TYPES
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Admits only callers with a credential [credentials] admits, Basic or
 * Bearer. Every state, every act and the bookmark need one. The shared,
 * content-addressed layers, the ALPS profiles and the OpenAPI description
 * stay open: they are the same for every caller. Each open path is matched whole, so no other path can
 * borrow its prefix.
 *
 * A refusal is a 401 problem offering both schemes in [realm]. A wrong
 * credential is answered exactly as a missing one.
 */
class HeartAuthFilter(private val credentials: HeartCredentials, realm: String) : WebFilter, Ordered {
    private val challenges = listOf("Basic realm=\"$realm\"", "Bearer realm=\"$realm\"")

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (open(exchange.request)) return chain.filter(exchange)
        val authorization = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        // A Basic check may run bcrypt, which blocks: never on the event loop.
        return Mono.fromCallable { credentials.admits(authorization) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { admitted -> if (admitted) chain.filter(exchange) else refuse(exchange) }
    }

    private fun open(request: ServerHttpRequest): Boolean =
        (request.method == HttpMethod.GET || request.method == HttpMethod.HEAD) &&
            OPEN.any { it.matches(request.path.pathWithinApplication().value()) }

    private fun refuse(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        response.headers[HttpHeaders.WWW_AUTHENTICATE] = challenges
        return response.writeWith(Mono.just(response.bufferFactory().wrap(PROBLEM)))
    }

    private companion object {
        private const val ADDRESS = "[0-9a-f]{16}"
        val OPEN = listOf(
            Regex("^${Layers.AFFORDANCE_SETS}$ADDRESS$"),
            Regex("^${Layers.SCHEMAS}$ADDRESS$"),
            Regex("^${Layers.EXPLANATIONS}$ADDRESS$"),
            Regex("^/profiles/[A-Za-z0-9_-]+$"),
            Regex("^${Regex.escape(OpenApiDocument.PATH)}$"),
            Regex("^${Regex.escape(SwaggerUiPage.PATH)}$"),
            Regex("^${Regex.escape(SwaggerUiPage.FILES)}/[A-Za-z0-9_-]+(\\.[A-Za-z0-9]+)+$"),
        )
        val PROBLEM = (
            """{"type":"${PROBLEM_TYPES}not-authenticated","title":"A credential is needed",""" +
                """"status":401,"detail":"This resource needs a Basic or Bearer credential the service admits."}"""
            ).toByteArray()
    }
}
