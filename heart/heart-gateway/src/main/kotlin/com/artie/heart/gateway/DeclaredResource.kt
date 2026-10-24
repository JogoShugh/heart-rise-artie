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
import com.artie.heart.NotOfferedException
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

    /** Each filter another operation serves: its route, its parameters' names, and where its items are. */
    private val searchedBy = declared.searchedBy.map { Served(it, spec.route(it.operation)) }

    // An act's relation is the file's name for it, shared by every resource that does it (github:close);
    // its own name is the resource's (issue-close), so OpenAPI's operations and schemas stay apart.
    private val actName = { act: ActDeclaration -> "${declared.noun}-${act.name}" }
    // Each act is served by its operation's own method, as GitHub serves it (SPEC §6.2).
    private val acts = declared.acts.map {
        val route = spec.route(it.operation)
        Act(actName(it), it.name, schema(it, route), it.name, method = route.method)
    }
    // A trial taking no care over offers reads no facts, and offers every act: the upstream says what it allows.
    private val keys = OfferKeys(acts, { answer ->
        val addressed = address?.of(answer) ?: answer
        if (GatewayCare.now().offers) {
            read(addressed.with(facts(addressed.body)))
        } else {
            Reading(Allowed(acts.map { it.relation }.toSet()), read(addressed).state)
        }
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
     * A trial may take neither care (GatewayCare): with no reread, the act's
     * own answer is the state where its operation is at the resource's own
     * path; where it isn't (a merge's), the resource is still read again.
     */
    private fun perform(checked: UpstreamState, act: ActDeclaration, input: JsonNode): Acted<UpstreamState> {
        val item = itemPath.expand(mapper.convertValue(checked.json, BODY))
        val care = GatewayCare.now()
        if (care.race) raced(item, checked, actName(act))
        val route = spec.route(act.operation)
        val sent = mapper.convertValue(input, BODY)
        val fields = sent.filterKeys { it in act.fields }.mapValues { (field, value) -> act.encoded(field, value) }
        val inputs = sent.filterKeys { it in act.inputs }.mapValues { (_, value) -> value.toString() }
        // A path variable the state doesn't fill is the client's field of that name: remove-label's {name}.
        val inPath = ActSchema(act, route, declared.file).pathFields(pointers.keys).toSet()
        // The form's names are the client's; the operation takes its own (a connector's rename).
        // A bound value goes where the operation names it: its path (a pull request's issue_number), else its body.
        val (boundInPath, boundInBody) = act.bound.entries.partition { it.key in ItemPath.variablesOf(route.path) }
        val variables = pointers.mapValues { (_, pointer) -> checked.json.at(pointer).asText() } +
            fields.filterKeys { it in inPath }.entries.associate { (field, value) -> act.upstream(field) to "$value" } +
            boundInPath.associate { (name, pointer) -> name to checked.json.at(pointer).asText() }
        val body = act.filled(inputs) +
            fields.filterKeys { it !in inPath }.mapKeys { (field, _) -> act.upstream(field) } +
            boundInBody.associate { (name, pointer) ->
                name to mapper.convertValue(checked.json.at(pointer), Any::class.java)
            }
        val statuses = RefusalStatuses(act.stale, act.refused)
        val path = ItemPath.fill(route.path, variables)
        val answer = calls.act(route.method, path, mapper.writeValueAsString(body), statuses)
        // An act that makes another resource names it in Location (201): where the act says it is, or as its answer.
        val created = act.at?.let { ItemPath.fill(it, variables + inputs) }
            ?: act.creates?.let { locate(it, answer.body) }
        val left = if (!care.reread && route.path == reading.path) answer else calls.get(item)
        return Acted(keys.state(left), created = created)
    }

    /**
     * [item] read again just before the act [name]: stale if it changed since [checked], its volatile members
     * aside; refused if those members, worked out meanwhile, mean the act is no longer offered.
     */
    private fun raced(item: String, checked: UpstreamState, name: String) {
        val now = keys.state(calls.get(item))
        if (declared.steady(now.json) != declared.steady(checked.json)) {
            throw ConcurrentWriteException("$item changed upstream since it was checked")
        }
        if (name !in keys.offers.offered(now.key)) throw NotOfferedException(keys.offers.whyAbsent(now.key, name))
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

    /**
     * The fields of the forms: the read's and the search's path parameters, then the filters, in order, then each
     * filter another operation serves, as that operation describes it.
     */
    private fun fields(search: SpecRoute): List<SearchField> {
        val parameters = (reading.parameters + search.parameters + searchedBy.flatMap { it.route.parameters })
            .distinctBy { it.name }.associateBy { it.name }
        val names = ItemPath.variablesOf(search.path) + itemPath.variables + declared.filters +
            searchedBy.map { it.by.filter }
        return names.distinct().map { name ->
            val parameter = checkNotNull(parameters[name]) { "${declared.file}: no parameter $name" }
            SearchField(name, name.replaceFirstChar { it.uppercase() }, parameter.description, parameter.schema)
        }
    }

    /**
     * Every match, by the search form's values. A value for a filter another operation serves sends the search
     * there, with every filter that operation has; of what it lists, only these resources are kept.
     */
    private fun find(search: SpecRoute, values: SearchValues): List<Found> {
        val by = searchedBy.firstOrNull { values[it.by.filter] != null }
        val route = by?.route ?: search
        val names = by?.let { other -> (declared.filters + other.by.filter).filter { it in other.names } }
            ?: declared.filters
        val path = ItemPath.fill(route.path, values.filterKeys { it in ItemPath.variablesOf(route.path) })
        val query = names.mapNotNull { name ->
            values[name]?.let { "$name=" + URLEncoder.encode(it, Charsets.UTF_8) }
        }
        val first = if (query.isEmpty()) path else "$path?" + query.joinToString("&")
        return if (by == null) pages(first, values) else pages(first, values, by::kept) { item, _ -> by.found(item) }
    }

    /**
     * Every item listed at [first] and the pages after it, at each item's own path. [known] are the list's
     * own path variables, for items that don't name them: GitHub's listed branches name only themselves.
     * [kept] says which items are these resources, and [found] where each is.
     */
    private fun pages(
        first: String,
        known: Map<String, String>,
        kept: (JsonNode) -> Boolean = { !skipped(it) },
        found: (JsonNode, Map<String, String>) -> Found = ::found,
    ): List<Found> {
        val all = mutableListOf<Found>()
        var next: String? = first
        while (next != null) {
            val answer = calls.get(next)
            all += answer.body.filter(kept).map { found(it, known) }
            // GitHub's next link is absolute; the gateway calls its upstream by path.
            next = answer.links["next"]?.let { if (it.query.isEmpty()) it.path else "${it.path}?${it.query}" }
        }
        return all
    }

    /** A filter [by] another operation serves, at [route]. */
    private inner class Served(val by: SearchedBy, val route: SpecRoute) {
        val names = route.parameters.map { it.name }.toSet()
        private val at = ItemAddress(by.address, reading.path, declared.file)

        /** Whether [item] is one of these resources. */
        fun kept(item: JsonNode): Boolean = by.keep.all { it.holds(Answer(item, emptyMap(), "", null)) }

        /** [item] at its own address, as one of these resources. */
        fun found(item: JsonNode): Found {
            val addressed = at.of(item)
            val variables = at.pointers.mapValues { (_, pointer) -> addressed.at(pointer).asText() }
            val title = declared.title?.let { item.at(it).asText() } ?: ItemPath.fill(reading.path, variables)
            return Found(item.path("id").asLong(), title, variables)
        }
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
