package com.artie.heart.spring

import com.artie.heart.OpenApiDocument
import com.artie.heart.Rung
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.server.RequestPredicate
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.coRouter

/**
 * Turns a server's declared resource types (its [HeartResource] beans) into
 * HEART over HTTP. Spring Boot applies it from the library's
 * `AutoConfiguration.imports`; a test slice imports it by name. Both
 * dialects are rendered by heart-core as plain JSON, so no hypermedia
 * framework is needed.
 */
@AutoConfiguration
@Import(HeartRegistry::class, HeartHandlers::class, HeartContentController::class, HeartProfileController::class)
class HeartAutoConfiguration {

    /**
     * For each declared collection: Layer 1 at `/{collection}/{id}` and its
     * acts at `/{collection}/{id}/actions/{path}`; the bookmark's own acts at
     * `/actions/{path}`; for a type that keeps its past, its state as of
     * `?asOf=` and its `/history`. Every refusal is
     * answered as SPEC §6.2 says.
     */
    @Bean
    fun heartRoutes(
        handlers: HeartHandlers,
        index: HeartIndexHandler,
        openApi: OpenApiHandler,
        swaggerUi: SwaggerUiPage,
    ): RouterFunction<ServerResponse> =
        coRouter {
            GET("/") { index.index(it) }
            GET(OpenApiDocument.PATH) { openApi.serve(it) }
            GET(SwaggerUiPage.PATH) { swaggerUi.serve(it) }
            POST("/actions/{path}") { handlers.actAtBookmark(it) }
            handlers.all.forEach { handler ->
                val resource = handler.resource
                val collection = resource.layers.type.collection
                if (resource.find != null) GET("/$collection") { index.search(resource, it) }
                GET("/$collection/{id}", NO_QUERY) { handler.read(it) }
                POST("/$collection/{id}/actions/{path}") { handler.act(it) }
                if (resource.past != null) {
                    GET("/$collection/{id}", queryParam("asOf") { true }) { handler.readAsOf(it) }
                    GET("/$collection/{id}/history") { handler.history(it) }
                }
            }
            onError<Throwable> { failure, _ -> HttpRefusals.of(failure) ?: throw failure }
        }

    /**
     * The bookmark's representations, from every declared type. `heart.title`
     * names the API; by default, its first profile's title does.
     */
    @Bean
    @ConditionalOnMissingBean
    fun serviceIndex(
        registry: HeartRegistry,
        @Value("\${heart.title:}") title: String,
    ): ServiceIndex =
        ServiceIndex(registry.layers.map { it.type }, title.ifBlank { null }, serviceDesc = OpenApiDocument.PATH)

    /**
     * Everything the server could ever be asked, as OpenAPI 3.1, from every
     * declared type. `heart.openapi.version` is its `info.version`: the
     * server's owner bumps it when the declarations change.
     */
    @Bean
    @ConditionalOnMissingBean
    fun openApiDocument(
        registry: HeartRegistry,
        @Value("\${heart.title:}") title: String,
        @Value("\${heart.openapi.version:${OpenApiDocument.DEFAULT_VERSION}}") version: String,
    ): OpenApiDocument = OpenApiDocument(registry.resources, title.ifBlank { null }, version)

    @Bean
    fun openApiHandler(document: OpenApiDocument) = OpenApiHandler(document)

    /** Swagger UI at `/docs`, reading the description above. */
    @Bean
    fun swaggerUiPage() = SwaggerUiPage()

    /** Shared by every binding a server uses; the first auto-configuration to run defines it. */
    @Bean
    @ConditionalOnMissingBean
    fun servingLimits(
        @Value("\${heart.max-rung:L2}") maxRung: Rung,
        @Value("\${heart.page-size:${ServingLimits.DEFAULT_PAGE_SIZE}}") pageSize: Int,
    ): ServingLimits = ServingLimits(maxRung, pageSize)

    /**
     * Who the service admits: `HEART_AUTH_BASIC_USERS` and `HEART_AUTH_BEARER_TOKENS`.
     * With neither set, the service does not start.
     */
    @Bean
    @ConditionalOnMissingBean
    fun heartCredentials(
        @Value("\${heart.auth.basic.users:}") users: String,
        @Value("\${heart.auth.bearer.tokens:}") tokens: String,
    ): HeartCredentials = HeartCredentials.parse(users, tokens)

    /** The realm of each challenge is `heart.auth.realm`, or else the first declared profile's name. */
    @Bean
    fun heartAuthFilter(
        credentials: HeartCredentials,
        registry: HeartRegistry,
        @Value("\${heart.auth.realm:}") realm: String,
    ) = HeartAuthFilter(credentials, realm.ifBlank { registry.profiles.keys.first() })

    @Bean
    fun heartIndexHandler(index: ServiceIndex, limits: ServingLimits) = HeartIndexHandler(index, limits)

    private companion object {
        val NO_QUERY = RequestPredicate { it.queryParams().isEmpty() }
    }
}
