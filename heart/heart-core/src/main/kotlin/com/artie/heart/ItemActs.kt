package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Acts served at a resource's own address (an [Act] declared `atItem`), such as a reply's progress, result and
 * failure, each a PATCH of the reply. They share the address and the method, so a request says which it makes by
 * the state its body names, in the member the resource's state is keyed by (`state` for a reply, `standing` for a
 * watch): the one each act's schema fixes with a `const`. The body must be of the type that act takes; any other
 * is refused 415, saying what the resource takes.
 */
object ItemActs {
    /** The member of a body that names the act it makes. */
    const val STATE = "state"

    private val mapper = ObjectMapper()

    /** The acts [type] serves at its items by [method]; none when it serves none there. */
    fun of(type: ResourceType<*, *>, method: String): List<Act> = type.acts.filter { it.atItem && it.method == method }

    /** The body types [type]'s items take by PATCH, for `Accept-Patch`; null when they take none. */
    fun acceptPatch(type: ResourceType<*, *>): String? =
        of(type, Act.PATCH).mapNotNull { it.contentType }.distinct().takeIf { it.isNotEmpty() }?.joinToString(", ")

    /**
     * The act a request by [method], with a body of [contentType] that reads [body], makes at an item of [type]:
     * the one whose schema fixes the `state` the body names. A state that names none is refused at `/state`; a
     * body of another type than that act takes, 415.
     */
    fun choose(type: ResourceType<*, *>, method: String, contentType: String?, body: String?): Act {
        val acts = of(type, method)
        val field = fieldOf(type)
        val named = body?.let { runCatching { mapper.readTree(it) }.getOrNull() }?.get(field)?.asText()
        val act = acts.firstOrNull { stateOf(it, field) == named } ?: throw UnprocessableContentException(
            listOf(Violation("/$field", "names none of ${acts.mapNotNull { stateOf(it, field) }.joinToString()}")),
        )
        val sent = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (sent != act.contentType) {
            val accepted = acts.mapNotNull { it.contentType }.distinct()
            throw UnsupportedMediaTypeException(
                "the state $named is sent as ${act.contentType}, not ${sent ?: "no type"}",
                method,
                accepted,
            )
        }
        return act
    }

    /** The member that names the act: the one the resource's state is keyed by, or `state`. */
    private fun fieldOf(type: ResourceType<*, *>): String = type.state.keyField ?: STATE

    /** The value [act]'s schema fixes for [field], if it fixes one. */
    private fun stateOf(act: Act, field: String): String? =
        mapper.readTree(act.schema).path("properties").path(field).path("const").takeUnless(JsonNode::isMissingNode)
            ?.asText()
}
