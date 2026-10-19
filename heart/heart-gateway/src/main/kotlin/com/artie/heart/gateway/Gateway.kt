package com.artie.heart.gateway

import com.artie.heart.Act
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.ContentAddress
import com.artie.heart.ContentAddress.canonical
import com.artie.heart.Finding
import com.artie.heart.Found
import com.artie.heart.HEART_RISE_PROFILE
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.http4k.core.HttpHandler
import org.http4k.core.then
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * A service that only publishes OpenAPI, presented to HEART clients as a
 * HEART server (spec/PLAN-OPENAPI-GATEWAY.md). Each collection in [spec]
 * becomes a resource type whose relations are named in one profile,
 * [profile], as the description names them (or after the operations' ids);
 * heart-http4k serves them. What each
 * state allows comes from the upstream itself: by the signal [connector]
 * names for its resource, or else by its HEART affordance link.
 */
// The upstream, its description, its connector and how to call it are each its own part;
// grouping them would only rename them.
@Suppress("LongParameterList")
class Gateway(
    spec: GatewaySpec,
    upstream: HttpHandler,
    profile: String,
    auth: UpstreamAuth = UpstreamAuth.None,
    private val mapper: ObjectMapper = HeartSettings.defaultMapper(),
    connector: Connector? = null,
    /** Where the upstream is: its links under this address are links into its API (spec/PLAN-GATEWAY-URL-LINKS.md). */
    upstreamUrl: String? = null,
    /** Where the gateway keeps its record of upstream calls (GATEWAY_RECORD); null when it keeps none. */
    private val record: UpstreamRecord? = null,
) {
    private val calls = Upstream(record?.around(upstream) ?: upstream, auth, mapper, connector?.headers.orEmpty())
    private val links = AffordanceLinks(calls)

    /** Which members of the upstream's answers are only links (Links.kt), when the connector says. */
    private val rules = connector?.links

    /** GET operations passed through without modelling them, with where a listed member's title is. */
    private val passed = connector?.passthrough.orEmpty()

    /**
     * Writes the links the connector's link rules lift: into the gateway where it serves them, typed. A link
     * to a passed-through read is named after its member, not its operation (decided 2026-10-18).
     */
    private val writer = rules?.let { rules ->
        val served = spec.routes.values.filter { it.method == "GET" }.map { route ->
            val relation = if (route.operation.name in passed) null else "$profile:${route.operation.relation}"
            ServedPath(route.path, relation)
        }
        LinkWriter(rules, upstreamUrl.orEmpty().trimEnd('/'), served, profile)
    }

    /**
     * The profile: every operation's and act's relation, and every relation the gateway's links and embedded
     * resources can use, worked out from the reads' answers before any is read (LinkRelations).
     */
    private val vocabulary = Profile(
        profile,
        spec.title,
        spec.doc,
        (relations(spec, connector) + linkRelations(spec)).distinctBy { it.id },
    )

    private fun linkRelations(spec: GatewaySpec): List<Relation> {
        val linked = rules ?: return emptyList()
        val reads = spec.routes.values.filter { it.method == "GET" }
        return writer?.let { LinkRelations(linked, it).of(reads) }.orEmpty()
    }

    /** The reads the connector passes through, served beside HEART's routes. */
    private val passthrough = rules?.let { linked ->
        writer?.let { Passthrough(spec, passed, calls, LinkLifter(linked, it, profile), it, mapper) }
    }

    init {
        connector?.check(spec)
    }

    /** The resource files' resources, by noun: an act of one may make another (a repository's open-issue). */
    private val declared: Map<String, DeclaredResource> by lazy {
        val standards = Standards(links.signal(byPath = emptyMap(), byRelation = emptyMap()))
        connector?.resources.orEmpty().associate { file ->
            val read = reader(connector, standards)
            val lifter = rules?.let { linked -> writer?.let { LinkLifter(linked.embedding(file.embed), it, profile) } }
            file.noun to DeclaredResource(file, spec, vocabulary, calls, read, mapper, lifter) { noun, body ->
                checkNotNull(declared[noun]) { "${file.file}: no resource $noun" }.location(body)
            }
        }
    }

    /**
     * One resource type per upstream collection. Each answer is read by the
     * first of the connector's readers that recognizes it, or else by a
     * standard the gateway detects by itself.
     */
    // A read passed through is not modelled, even where its path looks like a collection's (/users/{username}).
    val resources: List<HeartResource<*, *>> = spec.types.filter { it.read.name !in passed }.map { type ->
        val standards = Standards(
            links.signal(
                byPath = type.acts.associate { it.path to it.operation.name },
                byRelation = type.acts.associate { act -> act.operation.relation to act.operation.name },
            ),
        )
        GatewayResource(type, vocabulary, calls, reader(connector, standards), mapper).resource
    } + declared.values.map { it.resource }

    /** The gateway as one HTTP handler, admitting the callers [credentials] admits. */
    fun handler(credentials: Credentials, settings: HeartSettings = HeartSettings(mapper = mapper)): HttpHandler {
        val served = HeartHttp(resources, credentials, settings, passthrough?.routes.orEmpty())
        return record?.servingFilter?.then(served) ?: served
    }

    private companion object {
        /** Each answer is read by the first of the connector's readers that recognizes it, or else by a standard. */
        fun reader(connector: Connector?, standards: Standards): (Answer) -> Reading =
            { connector?.readerFor(it)?.read(it) ?: standards.read(it) }

        /**
         * Every operation's relation, as the description names it, or after its id: `switchOff` is `switch-off`.
         * A connector that includes only some operations has a relation for each act its resource files
         * declare, and for each included operation no act is made of. A read it passes through has none:
         * links to it are named after the member they were.
         */
        fun relations(spec: GatewaySpec, connector: Connector?): List<Relation> =
            if (connector?.include != null) {
                val acts = connector.resources.flatMap { it.acts }
                val modelled = spec.routes.values.filter { it.operation.name !in connector.passthrough }
                modelled.filter { route -> acts.none { it.operation == route.operation.name } }.map { route ->
                    relation(route.operation, if (route.method == "GET") AlpsType.SAFE else AlpsType.UNSAFE)
                } + acts.distinctBy { it.name }.map { act -> // close, reopen, edit: one relation, any resource
                    Relation(act.name, AlpsType.UNSAFE, act.title, act.description, act.description)
                }
            } else {
                spec.types.flatMap { type ->
                    (type.acts + type.bookmarkActs).map { relation(it.operation, AlpsType.UNSAFE) } +
                        listOfNotNull(type.search?.operation, type.read).map { relation(it, AlpsType.SAFE) }
                }.distinctBy { it.id }
            }

        fun relation(operation: SpecOperation, type: AlpsType) =
            Relation(id = operation.relation, type, operation.title, operation.doc, operation.doc)
    }
}

/**
 * The signals the gateway reads with no connector, because they are
 * standards: HEART's affordance link, then OData 4.01's advertised actions.
 * An answer with neither is refused: the gateway never guesses what is allowed.
 */
internal class Standards(private val affordanceLink: AllowedNow) {
    private val odata = Reader("OData 4.01", Match(), ODataActions(namespace = null), emptyList(), emptySet())

    fun read(answer: Answer): Reading = when {
        FORMS in answer.links -> Reading(affordanceLink.of(answer.body, answer.links), answer.body)
        ODataActions(namespace = null).advertises(answer.body) -> odata.read(answer)
        else -> error("${answer.path}: no reader recognizes the answer, and it carries no standard signal")
    }

    private companion object {
        const val FORMS = "$HEART_RISE_PROFILE/rels/forms"
    }
}

/**
 * A relation's id from an operation's, when the description names none:
 * `switchOff` is `switch-off`, and GitHub's `pulls/merge` is `pulls-merge`.
 */
internal fun relationOf(operationId: String): String =
    operationId.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace('/', '-').lowercase()

/** A member of an upstream collection as last read: its id, its plain JSON, and the acts it allows now, as its key. */
data class UpstreamState(val id: Long, val json: JsonNode, val key: String)

/**
 * One upstream collection as a HEART resource type. Its offers are open: a
 * key is the set of acts the upstream allows a member now, met at run time.
 * An act is checked, then acted: the member is read again just before the
 * act, and if it is no longer the state the client's validator matched, the
 * act is stale. Without the upstream's own If-Match, a write that lands in
 * between can't be caught; that is the weaker guarantee the plan names.
 */
internal class GatewayResource(
    private val spec: SpecType,
    profile: Profile,
    private val calls: Upstream,
    /** What an answer allows now, and its state alone. */
    private val read: (Answer) -> Reading,
    private val mapper: ObjectMapper,
) {
    private val acts = spec.acts.map { act(it) }
    private val bookmarkActs = spec.bookmarkActs.map { act(it) }
    private val keys = OfferKeys(acts, read)

    private val type = ResourceType<UpstreamState, String>(
        collection = spec.collection,
        profile = profile,
        acts = acts,
        offers = keys.offers,
        state = StateView(
            keyOf = { it.key },
            idOf = { it.id },
            // The ETag also names the representation's digest, so the body itself tells states apart.
            versionOf = { 0 },
            bodyOf = { mapper.convertValue(it.json, BODY) },
        ),
        finding = spec.search?.let { search ->
            Finding(
                noun = spec.noun,
                search = Named(search.operation.name, search.operation.relation),
                lookup = Named(spec.read.name, spec.read.relation), // as the description names them
                nameDescription = search.nameDescription,
                idTitle = "${spec.noun.replaceFirstChar { it.uppercase() }} id",
                idDescription = search.idDescription,
            )
        },
        bookmarkActs = bookmarkActs,
    )

    val resource: HeartResource<UpstreamState, String> = HeartResource(
        layers = Layers(type),
        load = { id -> state(calls.get("/${spec.collection}/$id")) },
        perform = { checked, name, input ->
            if (read(calls.get("/${spec.collection}/${checked.id}")).state != checked.json) {
                throw ConcurrentWriteException("${spec.noun} ${checked.id} changed upstream since it was checked")
            }
            val path = acts.first { it.name == name }.path
            Acted(state(calls.post("/${spec.collection}/${checked.id}/actions/$path", input.json.toString())))
        },
        find = if (spec.search == null) null else { name, id -> find(name, id) },
        performAtBookmark = if (bookmarkActs.isEmpty()) null else { name, input ->
            val path = bookmarkActs.first { it.name == name }.path
            Acted(state(calls.post("/actions/$path", input.json.toString())))
        },
    )

    private fun state(answer: Answer): UpstreamState = keys.state(answer)

    /** Every match, a page at a time, as the upstream's plain search pages list them. */
    private fun find(name: String?, id: Long?): List<Found> {
        val query = listOfNotNull(
            name?.let { "name=" + URLEncoder.encode(it, Charsets.UTF_8) },
            id?.let { "id=$it" },
        )
        val found = mutableListOf<Found>()
        var page = 1
        do {
            val answer = calls.get("/${spec.collection}?" + (query + "page=$page").joinToString("&")).body
            val items = answer.path("items").map { Found(it.path("id").asLong(), it.path("title").asText()) }
            found += items
            page++
        } while (items.isNotEmpty() && found.size < answer.path("count").asInt())
        return found
    }

    private companion object {
        val BODY = object : TypeReference<Map<String, Any?>>() {}

        /** An operation as an act, known by the relation the description names. */
        fun act(spec: SpecAct) = Act(spec.operation.name, spec.operation.relation, spec.schema, spec.path)
    }
}
