package com.artie.heart.spring

import com.artie.heart.Caller
import com.artie.heart.Listing
import com.artie.heart.OpenApiDocument
import com.artie.heart.Rung
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import kotlinx.coroutines.asContextElement
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
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
    // Spring hands the routes each bean they serve, one parameter each; grouping them would only rename them.
    @Suppress("LongParameterList")
    fun heartRoutes(
        handlers: HeartHandlers,
        index: HeartIndexHandler,
        openApi: OpenApiHandler,
        swaggerUi: SwaggerUiPage,
        listings: ObjectProvider<Listing>,
        auth: HeartAuthFilter,
    ): RouterFunction<ServerResponse> =
        coRouter {
            // Every read, act and search is handed the credential its request carried (Caller),
            // on whichever thread the handler's coroutine resumes.
            context { request ->
                Caller.holder.asContextElement(request.headers().firstHeader(HttpHeaders.AUTHORIZATION))
            }
            GET("/") { index.index(it) }
            // Lists the domain declares for its forms' choices (HAL-SCHEMA-FORMS-HR Rule 8).
            listings.orderedStream().forEach { listing ->
                val handler = ListingHandler(listing)
                GET(listing.path) { handler.serve(it) }
            }
            GET(OpenApiDocument.PATH) { openApi.serve(it) }
            GET(SwaggerUiPage.PATH) { swaggerUi.serve(it) }
            POST("/actions/{path}") { handlers.actAtBookmark(it) }
            handlers.all.forEach { handler ->
                val resource = handler.resource
                val collection = resource.layers.type.collection
                val item = resource.layers.type.itemPath.template
                val searchedAt = resource.layers.type.finding?.collectionPath ?: "/$collection"
                if (resource.find != null || resource.search != null) GET(searchedAt) { index.search(resource, it) }
                GET(item, NO_QUERY) { handler.read(it) }
                resource.lists.keys.forEach { relation ->
                    GET(resource.layers.type.subCollections.getValue(relation)) { index.listed(resource, relation, it) }
                }
                // Any method: the act says which it is served by, and refuses another with 405 (SPEC §6.2).
                path("$item/actions/{path}").invoke { handler.act(it) }
                if (resource.past != null) {
                    GET(item, queryParam("asOf") { true }) { handler.readAsOf(it) }
                    GET("$item/history") { handler.history(it) }
                }
            }
            onError<Throwable> { failure, _ -> HttpRefusals.of(failure, auth.challenges) ?: throw failure }
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
        limits: ServingLimits,
    ): OpenApiDocument = OpenApiDocument(registry.resources, title.ifBlank { null }, version, limits.affordanceLinks)

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
        @Value("\${heart.affordance-links:false}") affordanceLinks: Boolean,
    ): ServingLimits = ServingLimits(maxRung, pageSize, affordanceLinks)

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
