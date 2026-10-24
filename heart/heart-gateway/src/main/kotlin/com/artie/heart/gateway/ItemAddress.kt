package com.artie.heart.gateway

import com.artie.heart.ItemPath
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.URLDecoder

/**
 * The read's path variables, matched from an item's own URL when its state
 * names its parts nowhere else: GitHub's issue says
 * `https://api.github.com/repos/acme/rockets/issues/10`, and has no owner
 * or repository field. They are kept in the state as `hr:address`, so a
 * shared form's template pointers fill its target from the state as ever.
 */
internal class ItemAddress(private val url: JsonPointer, private val readPath: PathTemplate, private val file: String) {
    private val variables = ItemPath.variablesOf(readPath)
    private val pattern = Regex(
        "^" + Regex("\\{[^}/]+}").split(readPath).joinToString("([^/]+)") { Regex.escape(it) } + "$",
    )

    /** Each variable of the read's path, where in the state it is. */
    val pointers: Map<String, String> = variables.associateWith { "/$MEMBER/$it" }

    /** [body], given `hr:address`. */
    fun of(body: JsonNode): JsonNode {
        val path = URI(body.at(url).asText()).rawPath
        val match = checkNotNull(pattern.matchEntire(path)) { "$file: $path is not $readPath" }
        val copy = body.deepCopy<JsonNode>() as ObjectNode
        val address = copy.putObject(MEMBER)
        variables.forEachIndexed { i, name ->
            address.put(name, URLDecoder.decode(match.groupValues[i + 1], Charsets.UTF_8))
        }
        return copy
    }

    /** [answer], its body given `hr:address`. */
    fun of(answer: Answer): Answer = Answer(of(answer.body), answer.links, answer.path, answer.mediaType)

    companion object {
        /** The member of the state that holds the read's path variables, when they come from the item's URL. */
        const val MEMBER = "hr:address"
    }
}
