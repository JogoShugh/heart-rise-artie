package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Acts served at a resource's own address (an [Act] declared `atItem`), such as a reply's progress, result and
 * failure, each a PATCH of the reply. They share the address and the method, so a request says which it makes by
 * the `state` its body names: the one each act's schema fixes with a `const`. The body must be of the type that
 * act takes; any other is refused 415, saying what the resource takes.
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
        val named = body?.let { runCatching { mapper.readTree(it) }.getOrNull() }?.get(STATE)?.asText()
        val act = acts.firstOrNull { stateOf(it) == named } ?: throw UnprocessableContentException(
            listOf(Violation("/$STATE", "names none of ${acts.mapNotNull(::stateOf).joinToString()}")),
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

    /** The state [act]'s schema fixes, if it fixes one. */
    private fun stateOf(act: Act): String? =
        mapper.readTree(act.schema).path("properties").path(STATE).path("const").takeUnless(JsonNode::isMissingNode)
            ?.asText()
}
