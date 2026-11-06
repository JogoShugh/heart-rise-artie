package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** RFC 6901 JSON Pointer: "" is the whole document, "/a/0/b" walks down from it. */
object JsonPointer {

    /** The value [pointer] names in [root], or null if the pointer is malformed or leads nowhere. */
    fun resolve(root: JsonElement, pointer: String): JsonElement? = when {
        pointer.isEmpty() -> root
        !pointer.startsWith('/') -> null
        else -> pointer.substring(1).split('/').fold(root as JsonElement?) { node, token ->
            step(node, token.replace("~1", "/").replace("~0", "~"))
        }
    }

    private fun step(node: JsonElement?, token: String): JsonElement? = when (node) {
        is JsonObject -> node[token]
        is JsonArray -> token.takeIf { it.matches(INDEX) }?.toIntOrNull()?.let { node.getOrNull(it) }
        else -> null
    }

    private val INDEX = Regex("0|[1-9][0-9]*")
}
