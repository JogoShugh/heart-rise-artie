package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** A URI template, such as `/repos/{owner}/{repo}/pulls/{number}`. */
typealias UriTemplate = String

/** A JSON Pointer into a resource's state, such as `/base/owner`. */
typealias StatePointer = String

/**
 * Where each resource of a type lives: a URI template whose variables are
 * filled from the resource's own state, one JSON Pointer per variable. A
 * type that declares none lives at `/{collection}/{id}`, its id at `/id`.
 * Clients never build these addresses: they follow self links, and fill a
 * shared form's target from the state by the same pointers
 * (HAL-SCHEMA-FORMS-HR Rule 1).
 */
class ItemPath(val template: UriTemplate, val pointers: Map<String, StatePointer>) {
    /** The template's variables, in order. */
    val variables: List<String> = variablesOf(template)

    init {
        variables.forEach { require(it in pointers) { "item path variable $it has no pointer" } }
        pointers.keys.forEach { require(it in variables) { "the pointer $it names no variable of $template" } }
    }

    /** The address of the resource whose state reads as [body], every variable filled and encoded. */
    fun expand(body: Map<String, Any?>): String {
        val tree: JsonNode = MAPPER.valueToTree(body)
        val values = pointers.mapValues { (_, pointer) ->
            val value = tree.at(pointer)
            check(!value.isMissingNode && !value.isNull) { "the state has nothing at $pointer" }
            value.asText()
        }
        return fill(template, values)
    }

    companion object {
        private val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
        private val MAPPER = ObjectMapper()
        private const val UNRESERVED = "-._~"

        /** Where a type lives when it declares no item path: `/{collection}/{id}`. */
        fun of(collection: String) = ItemPath("/$collection/{id}", mapOf("id" to "/id"))

        /** The variables of [template], in order. */
        fun variablesOf(template: UriTemplate): List<String> =
            VARIABLE.findAll(template).map { it.groupValues[1] }.toList()

        /** [template] with each variable filled from [values] and encoded as a path segment. */
        fun fill(template: UriTemplate, values: Map<String, String>): String =
            VARIABLE.replace(template) { match ->
                encode(checkNotNull(values[match.groupValues[1]]) { "no value for ${match.groupValues[1]}" })
            }

        /** RFC 3986 section 3.3: a path segment keeps unreserved characters; everything else is percent-encoded. */
        private fun encode(value: String): String = buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val c = byte.toInt().toChar()
                if (byte >= 0 && (c.isLetterOrDigit() || c in UNRESERVED)) append(c) else append("%%%02X".format(byte))
            }
        }
    }
}
