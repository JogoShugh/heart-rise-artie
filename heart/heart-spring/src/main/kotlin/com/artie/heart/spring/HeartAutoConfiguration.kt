package com.artie.heart.spring

import com.artie.heart.ServiceIndex
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType
import org.springframework.web.reactive.function.server.RequestPredicate
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

/**
 * Turns a server's declared resource types (its [HeartResource] beans) into
 * HEART over HTTP. Spring Boot applies it from the library's
 * `AutoConfiguration.imports`; a test slice imports it by name. HAL-FORMS is
 * rendered by Spring HATEOAS, so its media type support is switched on here.
 */
@AutoConfiguration
@EnableHypermediaSupport(type = [HypermediaType.HAL_FORMS])
@Import(
    HeartRegistry::class, HeartHandlers::class, ServingLimits::class,
    HeartContentController::class, HeartProfileController::class,
)
class HeartAutoConfiguration {

    /**
     * For each declared collection: Layer 1 at `/{collection}/{id}` and its
     * acts at `/{collection}/{id}/actions/{path}`. A Layer 1 request with a
     * query string is left to the server's own routes (the garden's `asOf`,
     * for example). Every refusal is answered as SPEC §6.2 says.
     */
    @Bean
    fun heartRoutes(handlers: HeartHandlers, index: HeartIndexHandler): RouterFunction<ServerResponse> =
        coRouter {
            GET("/") { index.index(it) }
            handlers.all.forEach { handler ->
                val resource = handler.resource
                val collection = resource.layers.type.collection
                if (resource.find != null) GET("/$collection") { index.search(resource, it) }
                GET("/$collection/{id}", NO_QUERY) { handler.read(it) }
                POST("/$collection/{id}/actions/{path}") { handler.act(it) }
            }
            onError<Throwable> { failure, _ -> HttpRefusals.of(failure) ?: throw failure }
        }

    /**
     * The bookmark's representations, from every declared type. `heart.title`
     * names the API; by default, its first profile's title does.
     */
    @Bean
    fun serviceIndex(
        registry: HeartRegistry,
        @Value("\${heart.title:}") title: String,
    ): ServiceIndex =
        ServiceIndex(registry.layers.map { it.type }, title.ifBlank { null })

    @Bean
    fun heartIndexHandler(index: ServiceIndex, limits: ServingLimits) = HeartIndexHandler(index, limits)

    private companion object {
        val NO_QUERY = RequestPredicate { it.queryParams().isEmpty() }
    }
}
