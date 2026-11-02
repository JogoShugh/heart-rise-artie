package heart.mcp

import heart.FormChoices
import heart.HeartRepresentation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/**
 * The act schemas a model was shown in one session, by act: each is shown once while it stays the same, as an
 * L2 client fetches a shared form set once and knows it after by its address.
 */
class Shown {
    private val seen = mutableSetOf<Pair<String, JsonElement>>()

    /** Whether [act]'s [schema] is new to the model, remembering it if it is. */
    fun first(act: String, schema: JsonElement): Boolean = seen.add(act to schema)
}

/**
 * What the host's model is shown of a resource (SPEC §6.8): its state, the
 * acts it offers now with their titles, descriptions and schemas, and its
 * links by relation and title. Never a target, a method, a validator or an
 * address: ARTIE holds those, and builds every request from them.
 */
internal object Views {
    /** What an act shows for its schema when the model was shown that same schema before. */
    const val AS_BEFORE = "as before"

    /**
     * The view of [held], with [extra] members. Each act's schema shows its
     * choices from state (HAL-SCHEMA-FORMS-HR Rule 8) as an `enum`, resolved
     * by ARTIE, reading with [read] those that come from another resource.
     * With [shown], the schemas the model was already shown this session, an
     * act whose schema is the same shows its name and title and [AS_BEFORE].
     * The state shows the resource's own members, not HEART's `hr:` ones.
     */
    fun of(
        held: HeartRepresentation,
        extra: Map<String, JsonElement> = emptyMap(),
        read: (String) -> JsonElement? = { null },
        shown: Shown? = null,
    ): JsonObject = buildJsonObject {
        putJsonObject("resource") { put("state", ownMembers(held.state)) }
        putJsonArray("acts") {
            held.affordances.forEach { (name, form) ->
                val schema = withoutIds(FormChoices(form, held, read).shownSchema())
                val first = shown?.first(name, schema) ?: true
                addJsonObject {
                    put("name", name)
                    form.title?.let { put("title", it) }
                    if (first) form.description?.let { put("description", it) }
                    put("schema", if (first) schema else JsonPrimitive(AS_BEFORE))
                }
            }
        }
        putJsonArray("links") {
            links(held).forEach { (rel, title) ->
                addJsonObject {
                    put("rel", rel)
                    title?.let { put("title", it) }
                }
            }
        }
        // SPEC §6.1: each embedded resource, by relation and title, with its state; followed like a link.
        if (held.embeddedByRel.isNotEmpty()) {
            putJsonArray("embedded") {
                held.embeddedByRel.forEach { (rel, resources) ->
                    resources.forEach { resource ->
                        addJsonObject {
                            put("rel", rel)
                            resource.title?.let { put("title", it) }
                            put("state", resource.state)
                        }
                    }
                }
            }
        }
        extra.forEach { (name, value) -> put(name, value) }
    }

    /** What a model may follow: the [links], and each embedded resource by its relation and title. */
    fun followable(held: HeartRepresentation): List<Pair<String, String?>> =
        links(held) + held.embeddedByRel.flatMap { (rel, resources) -> resources.map { rel to it.title } }

    /**
     * The links a model may follow: every one but `self`, the affordance-set
     * link and the explanation link, which are ARTIE's plumbing, not choices,
     * and but those ARTIE can't follow: a template, or a link out of HEART.
     */
    fun links(held: HeartRepresentation): List<Pair<String, String?>> {
        val plumbing = setOfNotNull(held.forms?.href, held.manifest.unavailable?.href)
        return held.linksByRel.filterKeys { it != "self" }.flatMap { (rel, links) ->
            links.filter { it.href !in plumbing && it.followable }.map { rel to it.title }
        }
    }

    /** A state without the members HEART adds for clients (`hr:manifest` on a bookmark): ARTIE reads those. */
    private fun ownMembers(state: JsonElement): JsonElement =
        if (state is JsonObject) JsonObject(state.filterKeys { !it.startsWith("hr:") }) else state

    /** A schema's `$id` is the address it was served at; the model needs the schema, not where it lives. */
    private fun withoutIds(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterKeys { it != "\$id" }.mapValues { (_, v) -> withoutIds(v) })
        is JsonArray -> JsonArray(element.map(::withoutIds))
        else -> element
    }
}
