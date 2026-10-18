package heart.mcp

import heart.HeartRepresentation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/**
 * What the host's model is shown of a resource (SPEC §6.8): its state, the
 * acts it offers now with their titles, descriptions and schemas, and its
 * links by relation and title. Never a target, a method, a validator or an
 * address: ARTIE holds those, and builds every request from them.
 */
internal object Views {

    fun of(held: HeartRepresentation, extra: Map<String, JsonElement> = emptyMap()): JsonObject = buildJsonObject {
        putJsonObject("resource") { put("state", held.state) }
        putJsonArray("acts") {
            held.affordances.forEach { (name, form) ->
                addJsonObject {
                    put("name", name)
                    form.title?.let { put("title", it) }
                    form.description?.let { put("description", it) }
                    put("schema", withoutIds(form.schema))
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
     * link and the explanation link, which are ARTIE's plumbing, not choices.
     */
    fun links(held: HeartRepresentation): List<Pair<String, String?>> {
        val plumbing = setOfNotNull(held.forms?.href, held.manifest.unavailable?.href)
        return held.linksByRel.filterKeys { it != "self" }.flatMap { (rel, links) ->
            links.filter { it.href !in plumbing }.map { rel to it.title }
        }
    }

    /** A schema's `$id` is the address it was served at; the model needs the schema, not where it lives. */
    private fun withoutIds(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterKeys { it != "\$id" }.mapValues { (_, v) -> withoutIds(v) })
        is JsonArray -> JsonArray(element.map(::withoutIds))
        else -> element
    }
}
