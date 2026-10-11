package com.artie.heart.spring

import com.artie.heart.HeartResource
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.buildAndAwait

/** The HTTP handler of each declared resource type, built once. */
@Component
class HeartHandlers(registry: HeartRegistry, limits: ServingLimits, mapper: ObjectMapper, index: ServiceIndex) {
    private val handlers: Map<HeartResource<*, *>, HeartResourceHandler<*, *>> =
        registry.resources.associateWith { HeartResourceHandler(it, limits, mapper, index) }

    val all: Collection<HeartResourceHandler<*, *>> get() = handlers.values

    /** The bookmark's act at `/actions/{path}`, run by the handler of the type that declares it; else 404. */
    suspend fun actAtBookmark(request: ServerRequest): ServerResponse {
        val path = request.pathVariable("path")
        val handler = all.firstOrNull { handler -> handler.resource.layers.type.bookmarkActs.any { it.path == path } }
            ?: return ServerResponse.notFound().buildAndAwait()
        val act = handler.resource.layers.type.bookmarkActs.first { it.path == path }
        return handler.actAtBookmark(act.name, request)
    }

    /** The handler of [resource], typed by its state, for a server's own routes. */
    @Suppress("UNCHECKED_CAST")
    fun <S, K> of(resource: HeartResource<S, K>): HeartResourceHandler<S, K> =
        handlers.getValue(resource) as HeartResourceHandler<S, K>
}
