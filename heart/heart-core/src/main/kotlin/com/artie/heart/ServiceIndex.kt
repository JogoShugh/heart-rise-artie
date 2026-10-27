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
class ServiceIndex(
    private val types: List<ResourceType<*, *>>,
    title: String? = null,
    /**
     * Where the binding serves the OpenAPI description, if it does: linked
     * from the index and the home document as `service-desc` (RFC 8631).
     */
    private val serviceDesc: String? = null,
) {
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
    fun validator(dialect: Dialect): String = "\"index-${dialect.etagTag}-${ContentAddress.address(body(dialect))}\""

    /** The home document of a server that serves rungs up to [maxRung]. */
    fun home(maxRung: Rung): String =
        mapper.writeValueAsString(HomeDocument(found, this.title, serviceDesc).at(maxRung))

    /** The index's own links: itself and, when served, the OpenAPI description. */
    private fun links(): Map<String, Any> = mapOf("self" to mapOf("href" to "/")) +
        listOfNotNull(serviceDesc?.let { SERVICE_DESC to mapOf("href" to it, "type" to OpenApiDocument.MEDIA_TYPE) })

    fun homeValidator(maxRung: Rung): String = "\"index-home-${ContentAddress.address(home(maxRung))}\""

    private fun schemaFormsIndex(): Map<String, Any> {
        // HAL-SCHEMA-FORMS-HR Rules 6 and 7: keyed by relation, and self-describing.
        val forms = found.flatMap { type ->
            val finding = checkNotNull(type.finding)
            val forms = FindingForms(type)
            listOf(
                type.profile.key(finding.search.relation) to
                    schemaForm(type, forms.searchTarget, finding.search.relation, forms.searchSchema),
                type.profile.key(finding.lookup.relation) to
                    schemaForm(type, forms.lookupTarget, finding.lookup.relation, forms.lookupSchema),
            ) + type.bookmarkActs.map { type.profile.key(it.relation) to actForm(type, it) }
        }.toMap()
        return mapOf(
            "title" to title,
            "_links" to links() + ("curies" to found.flatMap { it.profile.curies }.distinct()),
            "_forms" to forms,
            MANIFEST to manifest(forms, types),
        )
    }

    /** The type that declares the index's act at [path], if any, and the act. */
    fun actAt(path: String): Pair<ResourceType<*, *>, Act>? =
        found.firstNotNullOfOrNull { type -> type.bookmarkActs.firstOrNull { it.path == path }?.let { type to it } }

    private fun halFormsIndex(): Map<String, Any> = mapOf(
        "title" to title,
        "_links" to links(),
        "_templates" to found.flatMap { type ->
            val finding = checkNotNull(type.finding)
            // A HAL-FORMS target cannot be templated, so a declared collection path has no template here.
            val searches = if (FindingForms(type).declared) emptyList() else listOf(
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
            )
            searches + type.bookmarkActs.map { act ->
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
        schema: Map<String, Any>,
    ): Map<String, Any> = mapOf(
        "_links" to mapOf("target" to mapOf("href" to target, "templated" to true)),
        "method" to "GET",
        "title" to type.profile.relation(relation).title,
        "description" to type.profile.relation(relation).description,
        "schema" to schema,
        // HAL-SCHEMA-FORMS-HR Rule 10: the bookmark's forms are pinned as a resource's are.
        FORM_INTEGRITY to FormIntegrity.of(target, "GET", FormIntegrity.ofSchema(schema)),
    )

    /** A HAL-FORMS GET template: its properties become the query string of the collection. */
    private fun halFormsTemplate(type: ResourceType<*, *>, relation: String, property: Map<String, Any>) =
        mapOf(
            "title" to type.profile.relation(relation).title,
            "method" to "GET",
            "target" to "/${type.collection}",
            "properties" to listOf(property),
        )

}

/** The body an act takes. */
private const val JSON = "application/json"

/** Where the index's own acts are: `/actions/{path}`, as a resource's are under it. */
const val ACTIONS = "/actions/"

/** RFC 8631: the relation of a link to a machine-readable description of the service. */
const val SERVICE_DESC = "service-desc"

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
        // Rule 10: over the schema as served, its $id with it, as the client reads it.
        FORM_INTEGRITY to
            FormIntegrity.of(ACTIONS + act.path, "POST", FormIntegrity.ofSchema(inline), contentType = JSON),
    )
}

/** HAL-SCHEMA-FORMS-HR Rule 10: a form's own integrity. */
private const val FORM_INTEGRITY = "hr:integrity"

/** Every form the server declares, by relation, with a digest of them all (spec/ideas/off-the-rails.md). */
private const val MANIFEST = "hr:manifest"

/**
 * The index's manifest: by relation, the integrity of each form the server can offer, the index's own [forms] and
 * every one of [types]' acts, sorted; and the integrity of that list in canonical form, which changes when any
 * form does. All of it is known from the declarations, with nothing read.
 */
private fun manifest(forms: Map<String, Map<String, Any>>, types: List<ResourceType<*, *>>): Map<String, Any> {
    val listed = (forms.map { (rel, form) -> rel to form.getValue(FORM_INTEGRITY) as String } +
        types.flatMap { Layers(it).formIntegrities.toList() })
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, integrities) -> integrities.distinct().sorted() }
        .toSortedMap()
    return mapOf("forms" to listed, "digest" to ContentAddress.integrity(ContentAddress.canonical(listed)))
}

/** SPEC §6.11.1: the index as a JSON Home document, for the types that can be found. */
internal class HomeDocument(
    private val found: List<ResourceType<*, *>>,
    private val title: String,
    private val serviceDesc: String?,
) {
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
            val forms = FindingForms(type)
            val (item, itemVars) = forms.item(VARS)
            val (collection, collectionVars) = forms.collection(VARS)
            listOf(
                (if (single) "item" else noun) to mapOf(
                    "hrefTemplate" to item,
                    "hrefVars" to itemVars,
                    "hints" to itemHints,
                ),
                (if (single) "collection" else type.collection) to mapOf(
                    "hrefTemplate" to collection,
                    "hrefVars" to collectionVars,
                    "hints" to mapOf("allow" to listOf("GET"), "formats" to FORMATS),
                ),
            )
        }.toMap()
        val links = mapOf("describedBy" to SPEC_URL) + listOfNotNull(serviceDesc?.let { SERVICE_DESC to it })
        val api = mapOf("title" to title, "links" to links)
        return mapOf("api" to api, "resources" to resources)
    }

    private companion object {
        const val SPEC_URL = "https://w3id.org/heart-rise/spec"
        const val VARS = "https://w3id.org/heart-rise/vars"

        /** Each dialect, with its heart-rise profile in the format object's `links` (link-hint `formats`). */
        val FORMATS: Map<String, Any> =
            Dialect.entries.associate { it.mediaType to mapOf("links" to mapOf("profile" to it.profile)) }
    }
}
