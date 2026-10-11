package com.artie.heart.spring

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/** The HTTP handler of each declared resource type, built once. */
@Component
class HeartHandlers(registry: HeartRegistry, limits: ServingLimits, mapper: ObjectMapper) {
    private val handlers: Map<HeartResource<*, *>, HeartResourceHandler<*, *>> =
        registry.resources.associateWith { HeartResourceHandler(it, limits, mapper) }

    val all: Collection<HeartResourceHandler<*, *>> get() = handlers.values

    /** The handler of [resource], typed by its state, for a server's own routes. */
    @Suppress("UNCHECKED_CAST")
    fun <S, K> of(resource: HeartResource<S, K>): HeartResourceHandler<S, K> =
        handlers.getValue(resource) as HeartResourceHandler<S, K>
}
