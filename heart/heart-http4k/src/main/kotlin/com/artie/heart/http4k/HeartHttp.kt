package com.artie.heart.http4k

import com.artie.heart.Caller
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Listing
import com.artie.heart.OpenApiDocument
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

/**
 * How a HEART server over http4k describes itself: its title (by default, its
 * first profile's), its OpenAPI `info.version`, how much it serves, the lists
 * a domain declares for its forms' choices, the realm of its challenges (by
 * default, its first profile's name), and the JSON mapper for bodies.
 */
class HeartSettings(
    val title: String? = null,
    val openApiVersion: String = OpenApiDocument.DEFAULT_VERSION,
    val limits: ServingLimits = ServingLimits(),
    val listings: List<Listing> = emptyList(),
    val realm: String? = null,
    val mapper: ObjectMapper = defaultMapper(),
) {
    companion object {
        /** Jackson as Spring Boot sets it up, so both bindings write and read bodies alike. */
        fun defaultMapper(): ObjectMapper = ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}

/**
 * HEART over HTTP, on http4k: a server's declared resource types turned into
 * one [HttpHandler], with the same routes, negotiation, validators, refusals
 * and credentials as heart-spring. For each declared collection: Layer 1 at
 * `/{collection}/{id}` and its acts at `/{collection}/{id}/actions/{path}`;
 * the bookmark at `/` and its acts at `/actions/{path}`; the search at
 * `/{collection}`; for a type that keeps its past, `?asOf=` and `/history`.
 */
class HeartHttp(
    resources: List<HeartResource<*, *>>,
    credentials: Credentials,
    settings: HeartSettings = HeartSettings(),
    /** More routes, behind the same credentials and refusals, after HEART's own: a gateway's pass-through reads. */
    extra: List<RoutingHttpHandler> = emptyList(),
) : HttpHandler {
    private val layers = resources.map { it.layers }
    private val index = ServiceIndex(layers.map { it.type }, settings.title, serviceDesc = OpenApiDocument.PATH)
    private val document =
        OpenApiDocument(resources, settings.title, settings.openApiVersion, settings.limits.affordanceLinks)
    private val published = Published(layers, document, settings.mapper)
    private val indexHandler = IndexHandler(index, settings.limits, settings.mapper)
    private val handlers = resources.map { ResourceHandler(it, settings.limits, settings.mapper, index) }
    private val realm = settings.realm ?: layers.first().type.profile.name

    private val app: HttpHandler = credentials.filter(realm)
        .then(Refusals.filter(settings.mapper, realm))
        // Every read, act and search is handed the credential its request carried (Caller).
        .then(Filter { next -> { request -> Caller.during(request.header("Authorization")) { next(request) } } })
        .then(routes(shared(settings.listings) + handlers.flatMap { routesOf(it) } + extra))

    override fun invoke(request: Request): Response = app(request)

    private fun shared(listings: List<Listing>): List<RoutingHttpHandler> = listOf(
        "/" bind GET to indexHandler::index,
        OpenApiDocument.PATH bind GET to published::openApi,
        SwaggerUi.PATH bind GET to SwaggerUi::page,
        SwaggerUi.files(),
        "${Layers.AFFORDANCE_SETS}{address}" bind GET to { published.affordanceSet(it.path("address").orEmpty()) },
        "${Layers.SCHEMAS}{address}" bind GET to { published.schema(it.path("address").orEmpty()) },
        "${Layers.EXPLANATIONS}{address}" bind GET to { published.explanation(it.path("address").orEmpty()) },
        "/profiles/{name}" bind GET to { published.profile(it.path("name").orEmpty()) },
        "/actions/{path}" bind POST to ::actAtBookmark,
    ) + listings.map { listing -> listing.path bind GET to { published.listing(listing, it) } }

    private fun routesOf(handler: ResourceHandler<*, *>): List<RoutingHttpHandler> {
        val resource = handler.resource
        val collection = resource.layers.type.collection
        val item = resource.layers.type.itemPath.template
        return listOfNotNull(
            (resource.find ?: resource.search)?.let {
                val searchedAt = resource.layers.type.finding?.collectionPath ?: "/$collection"
                searchedAt bind GET to { indexHandler.search(resource, it) }
            },
            item bind GET to handler::read,
        ) + resource.lists.keys.map { relation ->
            resource.layers.type.subCollections.getValue(relation) bind GET to {
                indexHandler.listed(resource, relation, it)
            }
        } + listOfNotNull(
            // Any method: the act says which it is served by, and refuses another with 405 (SPEC §6.2).
            "$item/actions/{path}" bind { request: Request -> handler.act(request) },
            resource.past?.let { "$item/history" bind GET to handler::history },
        )
    }

    /** The bookmark's act, run by the handler of the type that declares it; else 404. */
    private fun actAtBookmark(request: Request): Response {
        val path = request.path("path")
        val handler = handlers.firstOrNull { it.resource.layers.type.bookmarkActs.any { act -> act.path == path } }
            ?: return Response(Status.NOT_FOUND)
        val act = handler.resource.layers.type.bookmarkActs.first { it.path == path }
        return handler.actAtBookmark(act.name, request)
    }
}
