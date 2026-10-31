package com.artie.heart.http4k

import com.artie.heart.Dialect
import com.artie.heart.FieldTitle
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.HtmlList
import com.artie.heart.HtmlView
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
        val accept = request.header(ACCEPT)
        return when {
            Negotiation.wantsHtml(accept) -> HtmlView.indexValidator(index).let { etag ->
                Published.revalidated(request, etag, HtmlView.MEDIA_TYPE) { HtmlView.index(index, etag) }
                    .replaceHeader(VARY, ACCEPT)
            }
            Negotiation.wantsJsonHome(accept) ->
                Published.revalidated(request, index.homeValidator(limits.maxRung), Negotiation.JSON_HOME) {
                    index.home(limits.maxRung)
                }.replaceHeader(VARY, ACCEPT).replaceHeader(LINK, SERVICE_DESC_LINK)
            else -> dialect(request).let { dialect ->
                val answer = Published.revalidated(request, index.validator(dialect), dialect.mediaType) {
                    index.body(dialect)
                }
                layer1(answer, dialect)
                    // RFC 8631: the OpenAPI description, for a client that knows only this address.
                    .replaceHeader(LINK, "${Negotiation.profileLinks(dialect)}, $SERVICE_DESC_LINK")
            }
        }
    }

    fun search(resource: HeartResource<*, *>, request: Request): Response {
        val variables = resource.layers.type.finding?.collectionPath
            ?.let { path -> ItemPath.variablesOf(path).mapNotNull { name -> request.path(name)?.let { name to it } } }
            .orEmpty().toMap()
        val searched = runBlocking { resource.searched(variables) { request.query(it) } }
        return page(request, searched, FieldTitle.of(resource.layers.type.collection))
    }

    /** The sub-collection [relation] under the resource the request's path names, as a search's page. */
    fun listed(resource: HeartResource<*, *>, relation: String, request: Request): Response {
        val path = resource.layers.type.subCollections.getValue(relation)
        val variables = ItemPath.variablesOf(path).mapNotNull { name -> request.path(name)?.let { name to it } }.toMap()
        val listed = runBlocking { resource.listed(relation, variables) }
        return page(request, listed, FieldTitle.of(relation.substringAfter(':')))
    }

    /** A page of [searched], as the request asks: HTML titled [title], plain JSON, or a dialect. */
    private fun page(request: Request, searched: Pair<Search, List<Found>>, title: String): Response {
        val page = request.query("page")?.toIntOrNull() ?: 1
        val (search, found) = searched
        val accept = request.header(ACCEPT)
        val body = SearchPages.page(search, found, page, limits.pageSize)
        return when {
            Negotiation.wantsHtml(accept) ->
                Published.revalidated(request, HtmlList.validator(body), HtmlView.MEDIA_TYPE) {
                    HtmlList.page(body, title)
                }.replaceHeader(VARY, ACCEPT)
            Negotiation.wantsPlainJson(accept) -> Response(Status.OK).header(CONTENT_TYPE, JSON).header(VARY, ACCEPT)
                .body(mapper.writeValueAsString(SearchPages.plainPage(found, page, limits.pageSize)))
            else -> dialect(request).let { dialect ->
                layer1(
                    Published.revalidated(request, SearchPages.validator(body, dialect), dialect.mediaType) {
                        mapper.writeValueAsString(body)
                    },
                    dialect,
                )
            }
        }
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
