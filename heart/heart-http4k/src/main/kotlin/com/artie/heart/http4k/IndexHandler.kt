package com.artie.heart.http4k

import com.artie.heart.Dialect
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Negotiation
import com.artie.heart.OpenApiDocument
import com.artie.heart.SERVICE_DESC
import com.artie.heart.ItemPath
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

/**
 * The bookmark and the searches over HTTP (SPEC §6.11, §6.11.1): `/` as the
 * service index in either dialect, or as JSON Home when Accept ranks it above
 * both; and `/{collection}` as a page of a search. heart-core builds the
 * bodies; this adds the Layer 1 headers. All are revalidated on every use.
 */
class IndexHandler(
    private val index: ServiceIndex,
    private val limits: ServingLimits,
    private val mapper: ObjectMapper,
) {
    fun index(request: Request): Response {
        if (Negotiation.wantsJsonHome(request.header(ACCEPT))) {
            val body = index.home(limits.maxRung)
            return Published.revalidated(request, index.homeValidator(limits.maxRung), Negotiation.JSON_HOME) { body }
                .replaceHeader(VARY, ACCEPT).replaceHeader(LINK, SERVICE_DESC_LINK)
        }
        val dialect = dialect(request)
        val answer = Published.revalidated(request, index.validator(dialect), dialect.mediaType) { index.body(dialect) }
        return layer1(answer, dialect)
            // RFC 8631: the OpenAPI description, for a client that knows only this address.
            .replaceHeader(LINK, "${Negotiation.profileLinks(dialect)}, $SERVICE_DESC_LINK")
    }

    fun search(resource: HeartResource<*, *>, request: Request): Response {
        val variables = resource.layers.type.finding?.collectionPath
            ?.let { path -> ItemPath.variablesOf(path).mapNotNull { name -> request.path(name)?.let { name to it } } }
            .orEmpty().toMap()
        return page(request, runBlocking { resource.searched(variables) { request.query(it) } })
    }

    /** The sub-collection [relation] under the resource the request's path names, as a search's page. */
    fun listed(resource: HeartResource<*, *>, relation: String, request: Request): Response {
        val path = resource.layers.type.subCollections.getValue(relation)
        val variables = ItemPath.variablesOf(path).mapNotNull { name -> request.path(name)?.let { name to it } }.toMap()
        return page(request, runBlocking { resource.listed(relation, variables) })
    }

    private fun page(request: Request, searched: Pair<Search, List<Found>>): Response {
        val page = request.query("page")?.toIntOrNull() ?: 1
        val (search, found) = searched
        if (Negotiation.wantsPlainJson(request.header(ACCEPT))) {
            return Response(Status.OK).header(CONTENT_TYPE, JSON).header(VARY, ACCEPT)
                .body(mapper.writeValueAsString(SearchPages.plainPage(found, page, limits.pageSize)))
        }
        val dialect = dialect(request)
        val body = SearchPages.page(search, found, page, limits.pageSize)
        val answer = Published.revalidated(request, SearchPages.validator(body, dialect), dialect.mediaType) {
            mapper.writeValueAsString(body)
        }
        return layer1(answer, dialect)
    }

    private fun layer1(response: Response, dialect: Dialect): Response =
        response.replaceHeader("Cache-Control", "no-cache")
            .replaceHeader(LINK, Negotiation.profileLinks(dialect))
            .replaceHeader(VARY, "$ACCEPT, ${Negotiation.ACCEPT_PROFILE}")

    private fun dialect(request: Request): Dialect =
        Negotiation.dialect(request.header(ACCEPT), request.header(Negotiation.ACCEPT_PROFILE))

    private companion object {
        const val SERVICE_DESC_LINK =
            "<${OpenApiDocument.PATH}>; rel=\"$SERVICE_DESC\"; type=\"${OpenApiDocument.MEDIA_TYPE}\""
    }
}
