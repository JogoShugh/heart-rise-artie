package com.artie.heart.gateway

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.ItemPath
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Profile
import com.artie.heart.ResourceType
import com.artie.heart.SearchField
import com.artie.heart.SearchValues
import com.artie.heart.StateView
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URLEncoder

/**
 * A resource a connector's resource file declares, on an upstream whose
 * paths aren't HEART's: it lives at its read operation's path, filled from
 * its state by the file's pointers, and is found at its search operation's
 * path, filled from the form, with the file's filters in the query. A search
 * answer is a bare JSON array, paged by `Link: rel="next"`, as GitHub's are.
 * Each form's field keeps the description's words and schema. Its acts are
 * projections of the upstream's operations (ActDeclaration).
 */
internal class DeclaredResource(
    private val declared: ResourceDeclaration,
    private val spec: GatewaySpec,
    profile: Profile,
    private val calls: Upstream,
    read: (Answer) -> Reading,
    private val mapper: ObjectMapper,
) {
    private val reading = spec.route(declared.read)
    private val listing = declared.search?.let { spec.route(it) }
    private val itemPath = ItemPath(reading.path, declared.pointers)
    private val acts = declared.acts.map { Act(it.name, it.name, schema(it, spec.route(it.operation)), it.name) }
    private val keys = OfferKeys(acts) { answer -> read(answer.with(facts(answer.body))) }

    private val type = ResourceType<UpstreamState, String>(
        collection = declared.collection,
        profile = profile,
        acts = acts,
        offers = keys.offers,
        state = StateView(
            keyOf = { it.key },
            idOf = { it.id },
            // The ETag also names the representation's digest, so the body itself tells states apart.
            versionOf = { 0 },
            bodyOf = { mapper.convertValue(it.json, BODY) },
            itemPath = itemPath,
        ),
        finding = listing?.let { search ->
            Finding(
                noun = declared.noun,
                search = Named(search.operation.name, search.operation.relation),
                lookup = Named(reading.operation.name, reading.operation.relation),
                collectionPath = search.path,
                fields = fields(search),
            )
        },
    )

    val resource: HeartResource<UpstreamState, String> = HeartResource(
        layers = Layers(type),
        loadAt = { at -> keys.state(calls.get(ItemPath.fill(reading.path, at))) },
        perform = { checked, name, input -> perform(checked, declared.acts.first { it.name == name }, input.json) },
        search = listing?.let { search -> { values -> find(search, values) } },
    )

    /**
     * The act, as its operation: the fixed body, the client's fields and the
     * fields bound from [checked], the state the client's validator matched.
     * The resource is read again first; if it is no longer that state, the
     * act is stale. Then the resource is read again, as the act left it,
     * since an operation's answer may not be the resource (a merge's isn't).
     */
    private fun perform(checked: UpstreamState, act: ActDeclaration, input: JsonNode): Acted<UpstreamState> {
        val item = itemPath.expand(mapper.convertValue(checked.json, BODY))
        if (keys.state(calls.get(item)).json != checked.json) {
            throw ConcurrentWriteException("$item changed upstream since it was checked")
        }
        val route = spec.route(act.operation)
        val variables = declared.pointers.mapValues { (_, pointer) -> checked.json.at(pointer).asText() }
        val body = act.fixed + mapper.convertValue(input, BODY).filterKeys { it in act.fields } +
            act.bound.mapValues { (_, pointer) -> mapper.convertValue(checked.json.at(pointer), Any::class.java) }
        val statuses = RefusalStatuses(act.stale, act.refused)
        calls.act(route.method, ItemPath.fill(route.path, variables), mapper.writeValueAsString(body), statuses)
        return Acted(keys.state(calls.get(item)))
    }

    /** The answers the file's facts name, each read at its operation's path, filled from [state]. */
    private fun facts(state: JsonNode): Map<String, UpstreamBody> = declared.facts.mapValues { (_, fact) ->
        val route = spec.route(fact.operation)
        calls.get(ItemPath.fill(route.path, fact.pointers.mapValues { (_, at) -> state.at(at).asText() })).body
    }

    /** An act's schema: the operation body's properties the client fills, and nothing else. */
    private fun schema(act: ActDeclaration, route: SpecRoute): String {
        @Suppress("UNCHECKED_CAST")
        val properties = route.body?.get("properties") as? Map<String, Any?> ?: emptyMap()
        val required = (route.body?.get("required") as? List<*>).orEmpty()
        return mapper.writeValueAsString(
            mapOf(
                "\$schema" to "https://json-schema.org/draft/2020-12/schema",
                "type" to "object",
                "title" to act.title,
                "properties" to act.fields.associateWith { field ->
                    checkNotNull(properties[field]) { "${declared.file}: ${act.operation} has no field $field" }
                },
                "required" to act.fields.filter { it in required },
                "additionalProperties" to false,
            ),
        )
    }

    /** The fields of the forms: the read's and the search's path parameters, then the filters, in order. */
    private fun fields(search: SpecRoute): List<SearchField> {
        val parameters = (reading.parameters + search.parameters).distinctBy { it.name }.associateBy { it.name }
        val names = ItemPath.variablesOf(search.path) + itemPath.variables + declared.filters
        return names.distinct().map { name ->
            val parameter = checkNotNull(parameters[name]) { "${declared.file}: no parameter $name" }
            SearchField(name, name.replaceFirstChar { it.uppercase() }, parameter.description, parameter.schema)
        }
    }

    /** Every match, following the upstream's `next` links, at each item's own path. */
    private fun find(search: SpecRoute, values: SearchValues): List<Found> {
        val path = ItemPath.fill(search.path, values.filterKeys { it in ItemPath.variablesOf(search.path) })
        val query = declared.filters.mapNotNull { name ->
            values[name]?.let { "$name=" + URLEncoder.encode(it, Charsets.UTF_8) }
        }
        val found = mutableListOf<Found>()
        var next: String? = if (query.isEmpty()) path else "$path?" + query.joinToString("&")
        while (next != null) {
            val answer = calls.get(next)
            found += answer.body.map { found(it) }
            // GitHub's next link is absolute; the gateway calls its upstream by path.
            next = answer.links["next"]?.let { if (it.query.isEmpty()) it.path else "${it.path}?${it.query}" }
        }
        return found
    }

    private fun found(item: JsonNode): Found {
        val variables = declared.pointers.mapValues { (_, pointer) -> item.at(pointer).asText() }
        val title = declared.title?.let { item.at(it).asText() } ?: ItemPath.fill(reading.path, variables)
        return Found(item.path("id").asLong(), title, variables)
    }

    private companion object {
        val BODY = object : TypeReference<Map<String, Any?>>() {}
    }
}
