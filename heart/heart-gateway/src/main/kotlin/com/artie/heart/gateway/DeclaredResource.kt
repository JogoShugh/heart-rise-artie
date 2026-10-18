package com.artie.heart.gateway

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Hypermedia
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
// The file, the description and the upstream are each its own part; grouping them would only rename them.
@Suppress("LongParameterList")
internal class DeclaredResource(
    private val declared: ResourceDeclaration,
    private val spec: GatewaySpec,
    profile: Profile,
    private val calls: Upstream,
    read: (Answer) -> Reading,
    private val mapper: ObjectMapper,
    /** Lifts the state's link members into HEART links, when the connector says which they are. */
    private val lifter: LinkLifter? = null,
    /** Where a resource of another declared type lives, by its noun and the upstream's answer for it. */
    private val locate: (noun: String, body: JsonNode) -> String = { noun, _ -> error("no resource $noun") },
) {
    private val reading = spec.route(declared.read)
    private val listing = declared.search?.let { spec.route(it) }
    private val address = declared.address?.let { ItemAddress(it, reading.path, declared.file) }

    /** Each variable of the read's path, where in the state it is: by the file's pointers, or in `hr:address`. */
    private val pointers: Map<String, String> = address?.pointers ?: declared.pointers
    private val itemPath = ItemPath(reading.path, pointers)

    // An act's relation is the file's name for it, shared by every resource that does it (github:close);
    // its own name is the resource's (issue-close), so OpenAPI's operations and schemas stay apart.
    private val actName = { act: ActDeclaration -> "${declared.noun}-${act.name}" }
    private val acts = declared.acts.map { Act(actName(it), it.name, schema(it, spec.route(it.operation)), it.name) }
    private val keys = OfferKeys(acts, { answer ->
        val addressed = address?.of(answer) ?: answer
        read(addressed.with(facts(addressed.body)))
    }) { it.relation }

    // Lists under a resource, each linked from it by its operation's relation: this file's lists, served here,
    // and the lists another file searches by.
    private val subCollections = (declared.lists + declared.links).map { spec.route(it) }
        .associate { it.operation.relation to it.path }

    private val type = ResourceType<UpstreamState, String>(
        collection = declared.collection,
        profile = profile,
        subCollections = subCollections,
        acts = acts,
        offers = keys.offers,
        state = StateView(
            keyOf = { it.key },
            idOf = { it.id },
            // The ETag also names the representation's digest, so the body itself tells states apart.
            versionOf = { 0 },
            // The state without its link members, which become links and embedded resources (Links.kt).
            bodyOf = { mapper.convertValue(lifter?.lift(it.json)?.state ?: it.json, BODY) },
            itemPath = itemPath,
            hypermediaOf = { lifter?.lift(it.json)?.hypermedia ?: Hypermedia.NONE },
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
        perform = { checked, name, input -> perform(checked, declared.acts.first { actName(it) == name }, input.json) },
        search = listing?.let { search -> { values -> find(search, values) } },
        lists = declared.lists.map { spec.route(it) }.associate { list ->
            list.operation.relation to { at -> pages(ItemPath.fill(list.path, at), at) }
        },
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
        val sent = mapper.convertValue(input, BODY)
        val fields = sent.filterKeys { it in act.fields }.mapValues { (field, value) -> act.encoded(field, value) }
        val inputs = sent.filterKeys { it in act.inputs }.mapValues { (_, value) -> value.toString() }
        // A path variable the state doesn't fill is the client's field of that name: remove-label's {name}.
        val inPath = ActSchema(act, route, declared.file).pathFields(pointers.keys).toSet()
        val variables = pointers.mapValues { (_, pointer) -> checked.json.at(pointer).asText() } +
            fields.filterKeys { it in inPath }.mapValues { (_, value) -> value.toString() }
        val body = act.filled(inputs) + fields.filterKeys { it !in inPath } +
            act.bound.mapValues { (_, pointer) -> mapper.convertValue(checked.json.at(pointer), Any::class.java) }
        val statuses = RefusalStatuses(act.stale, act.refused)
        val path = ItemPath.fill(route.path, variables)
        val answer = calls.act(route.method, path, mapper.writeValueAsString(body), statuses)
        // An act that makes another resource names it in Location (201): where the act says it is, or as its answer.
        val created = act.at?.let { ItemPath.fill(it, variables + inputs) }
            ?: act.creates?.let { locate(it, answer.body) }
        return Acted(keys.state(calls.get(item)), created = created)
    }

    /** Where the resource the upstream answered [body] for lives, by this type's item path. */
    fun location(body: JsonNode): String =
        itemPath.expand(mapper.convertValue(address?.of(body) ?: body, BODY))

    /** The answers the file's facts name, each read at its operation's path, filled from [state]. */
    private fun facts(state: JsonNode): Map<String, UpstreamBody> = declared.facts.mapValues { (_, fact) ->
        val route = spec.route(fact.operation)
        calls.get(ItemPath.fill(route.path, fact.pointers.mapValues { (_, at) -> state.at(at).asText() })).body
    }

    /** An act's schema: the operation body's properties the client fills, and nothing else. */
    /** An act's form: the fields the client fills, as the operation describes them (ActSchema). */
    private fun schema(act: ActDeclaration, route: SpecRoute): String =
        mapper.writeValueAsString(ActSchema(act, route, declared.file).of(pointers.keys))

    /** The fields of the forms: the read's and the search's path parameters, then the filters, in order. */
    private fun fields(search: SpecRoute): List<SearchField> {
        val parameters = (reading.parameters + search.parameters).distinctBy { it.name }.associateBy { it.name }
        val names = ItemPath.variablesOf(search.path) + itemPath.variables + declared.filters
        return names.distinct().map { name ->
            val parameter = checkNotNull(parameters[name]) { "${declared.file}: no parameter $name" }
            SearchField(name, name.replaceFirstChar { it.uppercase() }, parameter.description, parameter.schema)
        }
    }

    /** Every match, by the search form's values. */
    private fun find(search: SpecRoute, values: SearchValues): List<Found> {
        val path = ItemPath.fill(search.path, values.filterKeys { it in ItemPath.variablesOf(search.path) })
        val query = declared.filters.mapNotNull { name ->
            values[name]?.let { "$name=" + URLEncoder.encode(it, Charsets.UTF_8) }
        }
        return pages(if (query.isEmpty()) path else "$path?" + query.joinToString("&"), values)
    }

    /**
     * Every item listed at [first] and the pages after it, at each item's own path. [known] are the list's
     * own path variables, for items that don't name them: GitHub's listed branches name only themselves.
     */
    private fun pages(first: String, known: Map<String, String>): List<Found> {
        val found = mutableListOf<Found>()
        var next: String? = first
        while (next != null) {
            val answer = calls.get(next)
            found += answer.body.filterNot { skipped(it) }.map { found(it, known) }
            // GitHub's next link is absolute; the gateway calls its upstream by path.
            next = answer.links["next"]?.let { if (it.query.isEmpty()) it.path else "${it.path}?${it.query}" }
        }
        return found
    }

    private fun skipped(item: JsonNode): Boolean =
        declared.skip.isNotEmpty() && declared.skip.all { it.holds(Answer(item, emptyMap(), "", null)) }

    private fun found(item: JsonNode, known: Map<String, String>): Found {
        // A listed item without its own address names its path's variables where the file's listed says;
        // the list's own path names the rest.
        val unaddressed = declared.address?.let { item.at(it).isMissingNode } != false
        val variables = if (declared.listed.isNotEmpty() && unaddressed) {
            itemPath.variables.associateWith { name ->
                declared.listed[name]?.let { item.at(it).asText() } ?: checkNotNull(known[name]) { "no $name" }
            }
        } else {
            val addressed = address?.of(item) ?: item
            pointers.mapValues { (_, pointer) -> addressed.at(pointer).asText() }
        }
        val title = declared.title?.let { item.at(it).asText() } ?: ItemPath.fill(reading.path, variables)
        return Found(item.path("id").asLong(), title, variables)
    }

    private companion object {
        val BODY = object : TypeReference<Map<String, Any?>>() {}
    }
}
