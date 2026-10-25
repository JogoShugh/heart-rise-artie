package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The words `by` and `says` (spec/PLAN-LISTENERS.md, slice 4): who made a resource, and what its words hold. A
 * resource names who made it as a resource of its own, embedded: a person, a bot or a team, under its author, user or
 * creator relation, whatever the profile it is named in.
 */

/** The relations a resource embeds who made it under, by their names in any profile. */
private val MAKERS = setOf("author", "user", "creator")

/** Whether this resource is one a Whenever keeps: made [by] whom it says, saying what it [says]; null, any. */
internal fun Representation.kept(by: String?, says: String?): Boolean =
    (by == null || by(by)) && (says == null || says(says))

/** Whether [who] made this resource: one it embeds as its maker is named [who], by its title or a value of its own. */
internal fun Representation.by(who: String): Boolean =
    embedded.filterKeys { it.substringAfterLast(':') in MAKERS }.values.flatten().any { maker ->
        maker.title.equals(who, ignoreCase = true) ||
            maker.state.values.any { (it as? JsonPrimitive)?.content.equals(who, ignoreCase = true) }
    }

/** Whether this resource's words hold [text], in any case: any text in its state. */
internal fun Representation.says(text: String): Boolean = words(state).any { it.contains(text, ignoreCase = true) }

private fun words(node: JsonElement): List<String> = when (node) {
    is JsonObject -> node.values.flatMap(::words)
    is JsonArray -> node.flatMap(::words)
    is JsonPrimitive -> listOfNotNull(node.content.takeIf { node.isString })
}
