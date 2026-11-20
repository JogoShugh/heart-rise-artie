package heart.cli.generate

/** Every leaf of a parsed JSON [node], each with its JSON Pointer from [at]: where a value stands in an answer. */
internal fun leaves(node: Any?, at: String): List<Pair<String, Any?>> = when (node) {
    is Map<*, *> -> node.entries.flatMap { (k, v) -> leaves(v, "$at/$k") }
    is List<*> -> node.flatMapIndexed { i, v -> leaves(v, "$at/$i") }
    else -> listOf(at to node)
}
