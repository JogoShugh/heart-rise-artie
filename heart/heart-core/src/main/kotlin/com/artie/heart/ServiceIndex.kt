package com.artie.heart

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * The bookmark's representations, built from the declared resource types
 * (SPEC §6.11, §6.11.1), the same for every binding:
 *
 * - the service index, which offers each type's search and lookup as GET
 *   forms. In HAL Schema Forms the targets are templates filled from the
 *   form's own fields (`/beds{?name}`, `/beds/{id}`). In HAL-FORMS a target
 *   cannot be templated, but a GET template puts its fields in the query
 *   (the HTML mutate-action-URL algorithm), so both target `/beds`;
 * - the JSON Home document (draft-nottingham-json-home), whose link hints
 *   (draft-ietf-httpapi-link-hint) say which dialects, which `Prefer` values
 *   and which preconditions the server takes;
 * - the pages of a search, which list matches under the IANA `item` relation
 *   (RFC 6573), titled, and page with first, last, next and prev (RFC 5005).
 *
 * The index ignores `Prefer`: its forms are inline at every rung.
 */
class ServiceIndex(types: List<ResourceType<*, *>>, title: String? = null) {
    private val found: List<ResourceType<*, *>> = types.filter { it.finding != null }
    private val mapper = ObjectMapper()
    val title: String = title ?: types.first().profile.title

    private val bodies: Map<Dialect, String> = mapOf(
        Dialect.JSONSCHEMA to mapper.writeValueAsString(schemaFormsIndex()),
        Dialect.HALFORMS to mapper.writeValueAsString(halFormsIndex()),
    )

    /** The index in [dialect]. */
    fun body(dialect: Dialect): String = bodies.getValue(dialect)

    /** The index's validator in [dialect]: it changes only when the body does. */
    fun validator(dialect: Dialect): String = "\"index-${dialect.etagTag}-${hex(body(dialect))}\""

    /** The home document of a server that serves rungs up to [maxRung]. */
    fun home(maxRung: Rung): String = mapper.writeValueAsString(HomeDocument(found, this.title).at(maxRung))

    fun homeValidator(maxRung: Rung): String = "\"index-home-${hex(home(maxRung))}\""

    private fun schemaFormsIndex(): Map<String, Any> = mapOf(
        "title" to title,
        "_links" to mapOf("self" to mapOf("href" to "/"), "curies" to found.flatMap { it.profile.curies }.distinct()),
        // HAL-SCHEMA-FORMS-HR Rules 6 and 7: keyed by relation, and self-describing.
        "_forms" to found.flatMap { type ->
            val finding = checkNotNull(type.finding)
            listOf(
                type.profile.key(finding.search.relation) to schemaForm(
                    type, "/${type.collection}{?name}", finding.search.relation, "name", nameProperty(finding),
                ),
                type.profile.key(finding.lookup.relation) to schemaForm(
                    type, "/${type.collection}/{id}", finding.lookup.relation, "id", idProperty(finding),
                ),
            ) + type.bookmarkActs.map { type.profile.key(it.relation) to actForm(type, it) }
        }.toMap(),
    )

    /** The type that declares the index's act at [path], if any, and the act. */
    fun actAt(path: String): Pair<ResourceType<*, *>, Act>? =
        found.firstNotNullOfOrNull { type -> type.bookmarkActs.firstOrNull { it.path == path }?.let { type to it } }

    private fun halFormsIndex(): Map<String, Any> = mapOf(
        "title" to title,
        "_links" to mapOf("self" to mapOf("href" to "/")),
        "_templates" to found.flatMap { type ->
            val finding = checkNotNull(type.finding)
            listOf(
                finding.search.name to halFormsTemplate(
                    type, finding.search.relation,
                    mapOf(
                        "name" to "name", "type" to "search", "required" to true, "minLength" to 1,
                        "prompt" to finding.nameDescription,
                    ),
                ),
                finding.lookup.name to halFormsTemplate(
                    type, finding.lookup.relation,
                    mapOf(
                        "name" to "id", "type" to "number", "required" to true, "min" to 1,
                        "prompt" to finding.idTitle,
                    ),
                ),
            ) + type.bookmarkActs.map { act ->
                act.name to mapOf(
                    "title" to type.profile.relation(act.relation).title,
                    "method" to "POST",
                    "contentType" to JSON,
                    "target" to ACTIONS + act.path,
                    "properties" to HalFormsProperties.of(act.schema),
                )
            }
        }.toMap(),
    )

    private fun schemaForm(
        type: ResourceType<*, *>,
        target: String,
        relation: String,
        field: String,
        property: Map<String, Any>,
    ): Map<String, Any> = mapOf(
        "_links" to mapOf("target" to mapOf("href" to target, "templated" to true)),
        "method" to "GET",
        "title" to type.profile.relation(relation).title,
        "description" to type.profile.relation(relation).description,
        "schema" to mapOf(
            "\$schema" to "https://json-schema.org/draft/2020-12/schema",
            "type" to "object",
            "properties" to mapOf(field to property),
            "required" to listOf(field),
        ),
    )

    /** A HAL-FORMS GET template: its properties become the query string of the collection. */
    private fun halFormsTemplate(type: ResourceType<*, *>, relation: String, property: Map<String, Any>) =
        mapOf(
            "title" to type.profile.relation(relation).title,
            "method" to "GET",
            "target" to "/${type.collection}",
            "properties" to listOf(property),
        )

    private fun hex(text: String): String = ContentAddress.address(text).removePrefix("sha256-")

}

/** The body an act takes. */
private const val JSON = "application/json"

/** Where the index's own acts are: `/actions/{path}`, as a resource's are under it. */
const val ACTIONS = "/actions/"

/**
 * An act the index offers: a form like any act's, targeting the index's own
 * `/actions/{path}`, with its schema inline (the index is the same at every
 * rung), named by content.
 */
private fun actForm(type: ResourceType<*, *>, act: Act): Map<String, Any> {
    val schema = ContentAddress.canonicalText(act.schema)
    @Suppress("UNCHECKED_CAST")
    val inline = LinkedHashMap(JsonTrees.plain(schema) as Map<String, Any?>)
    inline["\$id"] = Layers.SCHEMAS + ContentAddress.address(schema)
    val relation = type.profile.relation(act.relation)
    return mapOf(
        "_links" to mapOf("target" to mapOf("href" to ACTIONS + act.path)),
        "method" to "POST",
        "contentType" to JSON,
        "title" to relation.title,
        "description" to relation.description,
        "schema" to inline,
    )
}

/** The search form's one field: part of a resource's name. */
private fun nameProperty(finding: Finding): Map<String, Any> = mapOf(
    "type" to "string", "minLength" to 1, "title" to "Name", "description" to finding.nameDescription,
)

/** The lookup form's one field: a resource's id. */
private fun idProperty(finding: Finding): Map<String, Any> = mapOf(
    "type" to "integer", "minimum" to 1, "title" to finding.idTitle, "description" to finding.idDescription,
)

/** SPEC §6.11.1: the index as a JSON Home document, for the types that can be found. */
internal class HomeDocument(private val found: List<ResourceType<*, *>>, private val title: String) {
    /**
     * Each type's resources (`item`) and searches (`collection`). With more
     * than one type, each is keyed by its own noun and collection instead.
     */
    fun at(maxRung: Rung): Map<String, Any> {
        val acceptPrefer = maxRung.preferenceApplied?.split(",")?.map { it.trim() }
        val itemHints = buildMap<String, Any> {
            put("allow", listOf("GET"))
            put("formats", FORMATS)
            acceptPrefer?.let { put("accept-prefer", it) }
            put("precondition-req", listOf("etag"))
        }
        val single = found.size == 1
        val resources = found.flatMap { type ->
            val noun = checkNotNull(type.finding).noun
            listOf(
                (if (single) "item" else noun) to mapOf(
                    "hrefTemplate" to "/${type.collection}/{id}",
                    "hrefVars" to mapOf("id" to "$VARS/$noun-id"),
                    "hints" to itemHints,
                ),
                (if (single) "collection" else type.collection) to mapOf(
                    "hrefTemplate" to "/${type.collection}{?name,id}",
                    "hrefVars" to mapOf("name" to "$VARS/$noun-name", "id" to "$VARS/$noun-id"),
                    "hints" to mapOf("allow" to listOf("GET"), "formats" to FORMATS),
                ),
            )
        }.toMap()
        val api = mapOf("title" to title, "links" to mapOf("describedBy" to SPEC_URL))
        return mapOf("api" to api, "resources" to resources)
    }

    private companion object {
        const val SPEC_URL = "https://github.com/jogoshugh/heart-rise/spec"
        const val VARS = "https://github.com/jogoshugh/heart-rise/vars"

        /** Each dialect, with its heart-rise profile in the format object's `links` (link-hint `formats`). */
        val FORMATS: Map<String, Any> =
            Dialect.entries.associate { it.mediaType to mapOf("links" to mapOf("profile" to it.profile)) }
    }
}
