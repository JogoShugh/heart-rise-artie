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
 * segment in `/{collection}/{id}/actions/{path}`.
 */
data class Act(val name: String, val relation: String, val schema: String, val path: String = relation)

/**
 * Which acts each offer key allows. [offered] names them, in order; the acts
 * offered must be a function of the key alone, so that every resource with
 * one key shares one affordance set (the sharing property, SPEC §6.9).
 * [keys] lists every key, so every set is known in advance. [whyAbsent] says
 * why an act is absent under a key (SPEC §6.12): a function of the key and
 * the act alone, never of one resource.
 */
class Offers<K>(
    val keys: List<K>,
    val offered: (K) -> List<String>,
    val whyAbsent: (K, String) -> String,
)

/** How heart-core reads a resource's state [S]: its offer key, id, version and JSON body. */
class StateView<S, K>(
    val keyOf: (S) -> K,
    val idOf: (S) -> Long,
    /** Changes with every change of state; part of each Layer 1 validator. */
    val versionOf: (S) -> Int,
    /** The state as plain JSON values, with its `id` at the top level. */
    val bodyOf: (S) -> Map<String, Any?>,
)

/** A resource type, declared once: everything heart-core needs to render and act on it. */
class ResourceType<S, K>(
    val collection: String,
    val profile: Profile,
    val acts: List<Act>,
    val offers: Offers<K>,
    val state: StateView<S, K>,
) {
    init {
        acts.forEach { profile.relation(it.relation) }
        offers.keys.forEach { key ->
            val unknown = offers.offered(key) - acts.map { it.name }.toSet()
            require(unknown.isEmpty()) { "key $key offers undeclared acts $unknown" }
        }
    }

    fun act(name: String): Act = acts.firstOrNull { it.name == name } ?: throw IllegalArgumentException("no act $name")
}
