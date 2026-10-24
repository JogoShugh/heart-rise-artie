package com.artie.heart

/**
 * Part of a resource's state that is another resource, embedded the HAL way
 * (SPEC §6.1, `_embedded`): the object, or the array of objects, at [pointer]
 * in the state, under [relation], a relation of the type's profile. Each gets
 * a self link, [self] filled from its own members, and may embed its own
 * ([embedded], pointers into it). What is embedded leaves the state. An
 * embedded resource carries no forms (HAL-SCHEMA-FORMS-HR Rule 9): a client
 * follows it to act on it.
 */
class Embedded(
    val relation: String,
    val pointer: StatePointer,
    val self: ItemPath,
    val embedded: List<Embedded> = emptyList(),
) {
    /** This embedding's relation and every nested one's, to check against a profile. */
    val relations: List<String> get() = listOf(relation) + embedded.flatMap { it.relations }

    companion object {
        /**
         * Takes each of [declared] out of [body], a mutable copy of a state, and
         * returns them as HAL's `_embedded`, keyed by [profile]'s CURIE for each
         * relation. Nothing at a pointer, or JSON null, embeds nothing; the member
         * still leaves the state.
         */
        fun take(body: MutableMap<String, Any?>, declared: List<Embedded>, profile: Profile): Map<String, Any?> {
            val taken = LinkedHashMap<String, Any?>()
            declared.forEach { embedding ->
                when (val value = remove(body, embedding.pointer)) {
                    is Map<*, *> -> taken[profile.key(embedding.relation)] = embedding.resource(value, profile)
                    is List<*> -> taken[profile.key(embedding.relation)] =
                        value.filterIsInstance<Map<*, *>>().map { embedding.resource(it, profile) }
                    else -> Unit
                }
            }
            return taken
        }

        /** Removes the member at [pointer], copying each map on the way so [body]'s own values are untouched. */
        private fun remove(body: MutableMap<String, Any?>, pointer: StatePointer): Any? {
            val names = pointer.removePrefix("/").split("/").map { it.replace("~1", "/").replace("~0", "~") }
            var parent = body
            names.dropLast(1).forEach { name ->
                val child = parent[name] as? Map<*, *> ?: return null
                val copy = LinkedHashMap<String, Any?>(child.entries.associate { it.key.toString() to it.value })
                parent[name] = copy
                parent = copy
            }
            return parent.remove(names.last())
        }
    }

    private fun resource(value: Map<*, *>, profile: Profile): Map<String, Any?> {
        val members = LinkedHashMap<String, Any?>(value.entries.associate { it.key.toString() to it.value })
        val href = self.expand(members)
        val nested = take(members, embedded, profile)
        members["_links"] = mapOf("self" to mapOf("href" to href))
        if (nested.isNotEmpty()) members["_embedded"] = nested
        return members
    }
}
