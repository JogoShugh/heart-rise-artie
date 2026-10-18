package com.artie.heart.gateway

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind

/**
 * GitHub reads a connector lists but doesn't model, served by the gateway at
 * the upstream's own paths, as the caller (spec/PLAN-GATEWAY-URL-LINKS.md):
 * readable, not yet modelled. A single resource is its state, with its link
 * members lifted like any other answer's, and no forms. A list is a HEART
 * collection (SPEC §6.11): an `item` link for each member, titled from it by
 * the connector's pointer, each member embedded, and the upstream's next
 * page as the page's `next` link.
 */
internal class Passthrough(
    private val spec: GatewaySpec,
    private val operations: Map<OperationId, JsonPointer?>,
    private val calls: Upstream,
    private val lifter: LinkLifter,
    private val writer: LinkWriter,
    private val mapper: ObjectMapper,
) {
    val routes: List<RoutingHttpHandler> = operations.map { (operation, title) ->
        spec.route(operation).path bind Method.GET to { request: Request -> answer(request, title) }
    }

    private fun answer(request: Request, title: JsonPointer?): Response {
        val asked = request.uri.path + (request.uri.query.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        val upstream = calls.get(asked)
        val body = when (val json = upstream.body) {
            is ArrayNode -> page(asked, json, title, upstream.links["next"]?.toString())
            else -> checkNotNull(lifter.resource(json)) { "$asked: the upstream's answer names no address of its own" }
        }
        val bytes = mapper.writeValueAsString(body)
        return Response(Status.OK)
            .header("Content-Type", Dialect.JSONSCHEMA.mediaType)
            .header("Link", "<${Dialect.JSONSCHEMA.profile}>; rel=\"profile\"")
            .header("Cache-Control", "no-cache")
            .header("ETag", "\"pass.${ContentAddress.address(bytes)}\"")
            .body(bytes)
    }

    /** A list as a HEART collection: its members linked, titled, and embedded; its next page linked. */
    private fun page(asked: String, items: ArrayNode, title: JsonPointer?, next: String?): Map<String, Any?> {
        val members = items.mapNotNull { item -> lifter.resource(item)?.let { item to it } }
        val links = members.map { (item, resource) ->
            val self = ((resource["_links"] as Map<*, *>)["self"] as Map<*, *>)["href"]
            mapOf("href" to self) + (title?.let { mapOf("title" to item.at(it).asText()) } ?: emptyMap())
        }
        return buildMap {
            put("_links", buildMap {
                put("self", mapOf("href" to asked))
                next?.let { put("next", mapOf("href" to writer.address(it))) }
                put("item", links)
            })
            if (members.isNotEmpty()) put("_embedded", mapOf("item" to members.map { it.second }))
        }
    }
}
