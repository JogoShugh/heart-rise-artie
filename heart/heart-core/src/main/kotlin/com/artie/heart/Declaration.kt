package com.artie.heart

/** An ALPS descriptor's type: whether following the relation changes state. */
enum class AlpsType { SAFE, UNSAFE, IDEMPOTENT }

/**
 * What a relation means (SPEC §6.1, §8.5; HAL-SCHEMA-FORMS-HR Rules 6 and 7):
 * the descriptor a CURIE such as `garden:water-cell` expands to.
 */
data class Relation(
    val id: String,
    val type: AlpsType,
    val title: String,
    /** One or two sentences, shown inline on a form. */
    val description: String,
    /** The ALPS descriptor's doc: what the act does, the state it leads to, when it is offered. */
    val doc: String,
)

/**
 * A domain's vocabulary: its relations, served as one ALPS document at
 * `/profiles/{name}`, and named in representations by the CURIE `{name}`.
 * One table serves the forms, the index, the ALPS document and problem
 * bodies, so they cannot disagree about what a relation is called or means.
 */
class Profile(val name: String, val title: String, val doc: String, val relations: List<Relation>) {
    val path: String = "/profiles/$name"

    private val byId: Map<String, Relation> = relations.associateBy { it.id }

    /** The `_links.curies` entry every representation with relation-named forms declares. */
    val curies: List<Map<String, Any>> = listOf(mapOf("name" to name, "href" to "$path#{rel}", "templated" to true))

    fun relation(id: String): Relation = byId[id] ?: throw IllegalArgumentException("no relation $id in $name")

    /** A form's key in `_forms`: the CURIE of relation [id]. */
    fun key(id: String): String = "$name:$id"

    /** Relation [id] as a URI reference, resolved against the request URL. */
    fun reference(id: String): String = "$path#$id"

    /** The ALPS profile document (draft-amundsen-richardson-foster-alps). */
    val alps: Map<String, Any> = mapOf(
        "alps" to mapOf(
            "version" to "1.0",
            "title" to title,
            "doc" to mapOf("format" to "text", "value" to doc),
            "descriptor" to relations.map { r ->
                mapOf(
                    "id" to r.id,
                    "type" to r.type.name.lowercase(),
                    "title" to r.title,
                    "doc" to mapOf("format" to "text", "value" to r.doc),
                )
            },
        ),
    )

    companion object {
        const val ALPS_MEDIA_TYPE = "application/alps+json"
    }
}

/**
 * An act a resource can offer. [name] is its logical name (the HAL-FORMS
 * template name); [relation] is the id of its relation in the profile;
 * [schema] is the JSON Schema of its body, as JSON text; [path] is its
 * segment in `/{collection}/{id}/actions/{path}`. An act declared with
 * [of] has an [input] class, and its schema is generated from it.
 * [formerPaths] are segments the act was served at before it moved: a
 * request there runs the same act with the same checks, while forms name
 * only [path], and the OpenAPI description marks each one deprecated.
 * [options] says where a property's choices are (HAL-SCHEMA-FORMS-HR Rule 8);
 * its schema carries them as `hr:options`, and a value outside them is refused.
 */
data class Act(
    val name: String,
    val relation: String,
    val schema: String,
    val path: String = relation,
    val input: Class<*>? = null,
    val formerPaths: List<String> = emptyList(),
    val options: Map<String, Options> = emptyMap(),
) {
    /** This act with choices for some of its properties: its schema carries them as `hr:options`. */
    fun withOptions(choices: Map<String, Options>): Act =
        copy(schema = Options.merged(schema, choices), options = options + choices)

    /** Whether a request to [segment] reaches this act: its path, or one it was served at before. */
    fun answersAt(segment: String): Boolean = segment == path || segment in formerPaths

    companion object {
        /** An act whose body is [input]: its schema is generated from the class (see InputSchemas). */
        fun of(
            name: String,
            relation: String,
            input: Class<*>,
            path: String = relation,
            formerPaths: List<String> = emptyList(),
        ): Act = Act(name, relation, InputSchemas.of(input), path, input, formerPaths)
    }
}

/**
 * Which acts each offer key allows. [offered] names them, in order; the acts
 * offered must be a function of the key alone, so that every resource with
 * one key shares one affordance set (the sharing property, SPEC §6.9).
 * [keys] lists every key, so every set is known in advance. [whyAbsent] says
 * why an act is absent under a key (SPEC §6.12): a function of the key and
 * the act alone, never of one resource.
 *
 * Offers that are [open] may meet keys beyond [keys] at run time, as a server
 * that learns its acts from another API does: each new key's sets are
 * rendered the first time a state with it is, and shared from then on.
 */
class Offers<K>(
    val keys: List<K>,
    val offered: (K) -> List<String>,
    val whyAbsent: (K, String) -> String,
    val open: Boolean = false,
)

/**
 * How heart-core reads a resource's state [S]: its offer key, id, version and
 * JSON body. [bodyClass], when given, is the body's shape, as an act's input
 * class is the shape of its body: the OpenAPI description's schema of the state
 * is generated from it. [keyField] names the body's member that holds the
 * offer key; that member's schema is then the closed enum of every declared key.
 * [itemPath], when given, is where each resource lives, filled from its state;
 * by default `/{collection}/{id}`.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class StateView<S, K>(
    val keyOf: (S) -> K,
    val idOf: (S) -> Long,
    /** Changes with every change of state; part of each Layer 1 validator. */
    val versionOf: (S) -> Int,
    /** The state as plain JSON values, with its `id` at the top level. */
    val bodyOf: (S) -> Map<String, Any?>,
    val bodyClass: Class<*>? = null,
    val keyField: String? = null,
    val itemPath: ItemPath? = null,
)

/** A form's two names: its logical [name] (a HAL-FORMS template name) and its [relation] id in the profile. */
data class Named(val name: String, val relation: String)

/**
 * How a resource type is found from the service index (SPEC §6.11): a
 * search by part of a resource's name, and a lookup by id. [noun] names one
 * resource (`bed`), for the home document's variables; the words describe
 * the two fields to a person or a model.
 *
 * A type may instead declare its [collectionPath], a URI template such as
 * `/repos/{owner}/{repo}/pulls`, and its forms' [fields]: one for each
 * variable of the collection path and of the item path, and any filters,
 * which go in the search's query. Its search then takes the collection
 * path's variables and the filters, and its lookup the item path's variables.
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class Finding(
    val noun: String,
    val search: Named,
    val lookup: Named,
    val nameDescription: String = "",
    val idTitle: String = "",
    val idDescription: String = "",
    val collectionPath: UriTemplate? = null,
    val fields: List<SearchField> = emptyList(),
) {
    /** The fields that are no variable of [collectionPath] or of [itemPath]: the search's query. */
    fun filters(itemPath: ItemPath): List<SearchField> {
        val variables = ItemPath.variablesOf(collectionPath.orEmpty()) + itemPath.variables
        return fields.filter { it.name !in variables }
    }

    /** The field named [name]. */
    fun field(name: String): SearchField = fields.first { it.name == name }
}

/** A field of a search or lookup form: its name, its words, and its JSON Schema. */
data class SearchField(
    val name: String,
    val title: String,
    val description: String,
    val schema: Map<String, Any> = mapOf("type" to "string", "minLength" to 1),
)

/**
 * A resource type, declared once: everything heart-core needs to render and
 * act on it. [acts] are offered by the type's resources, as their state
 * allows. [bookmarkActs] are offered by the service index, always: acts
 * whose result is a resource of this type that did not exist before, such
 * as a customer reporting a problem. The index offers them beside the
 * type's search, so such a type is also found ([finding]).
 */
// A declaration names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class ResourceType<S, K>(
    val collection: String,
    val profile: Profile,
    val acts: List<Act>,
    val offers: Offers<K>,
    val state: StateView<S, K>,
    val finding: Finding? = null,
    val bookmarkActs: List<Act> = emptyList(),
) {
    /** Every act declared, the resources' and the bookmark's. */
    val allActs: List<Act> = acts + bookmarkActs

    /** Where each resource of this type lives. */
    val itemPath: ItemPath = state.itemPath ?: ItemPath.of(collection)

    init {
        allActs.forEach { profile.relation(it.relation) }
        finding?.let { profile.relation(it.search.relation) }
        finding?.let { profile.relation(it.lookup.relation) }
        require(bookmarkActs.isEmpty() || finding != null) { "$collection has bookmark acts, so it is found too" }
        val twice = allActs.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(twice.isEmpty()) { "acts declared twice: $twice" }
        offers.keys.forEach { checkKey(it) }
        finding?.collectionPath?.let { path ->
            val named = finding.fields.map { it.name }.toSet()
            (ItemPath.variablesOf(path) + itemPath.variables).distinct().forEach {
                require(it in named) { "$collection: no field for $it" }
            }
        }
    }

    /** Refuses a [key] that offers an act this type doesn't declare: at start, or when an open key is first met. */
    fun checkKey(key: K) {
        val unknown = offers.offered(key) - acts.map { it.name }.toSet()
        require(unknown.isEmpty()) { "key $key offers undeclared acts $unknown" }
    }

    /** Act [name], whoever offers it. */
    fun act(name: String): Act =
        allActs.firstOrNull { it.name == name } ?: throw IllegalArgumentException("no act $name")

    /** Whether the service index offers act [name]. */
    fun atBookmark(name: String): Boolean = bookmarkActs.any { it.name == name }
}
