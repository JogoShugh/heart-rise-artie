package heart.script

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The members of outcomes kept by name that a goal's step names, `{smoke.of}` (goal-rise.feature), each with its
 * value: what [step] sends that equals one is read from it again, before any answer that happened to hold the same
 * value. A source is `{kept: smoke, pointer: /of}`.
 */
internal class Citations(step: TraceStep, run: Run) {
    private val cited: List<Cited> =
        // The step's own words when it has them; a goal's step is recorded before them, so the goal's.
        CITED.findAll(listOfNotNull(step.fromIntent, step.intent, run.goal).joinToString(" ")).mapNotNull { found ->
            val (name, member) = found.destructured
            val state = run.kept[name]?.takeIf { it.inPlace }?.state?.root as? JsonObject
            (state?.get(member) as? JsonPrimitive)?.let { Cited(name, member, it.content) }
        }.distinctBy { it.name to it.member }.toList()

    /** The sources of [value] sent whole. */
    fun whole(value: String): List<JsonElement> = cited.filter { it.value == value }.map { it.source }

    /**
     * Each scalar of [text], a JSON object or array sent, that a cited member gave, by its pointer: the one whose
     * name is most like the member it is sent as (`passed` is `passing`), where more than one holds that value.
     */
    fun members(text: String): Map<String, JsonElement> {
        val parsed = cited.takeIf { it.isNotEmpty() }?.let { runCatching { Json.parseToJsonElement(text) }.getOrNull() }
        if (parsed !is JsonObject && parsed !is JsonArray) return emptyMap()
        return scalars(parsed, "").mapNotNull { (pointer, value) ->
            val key = pointer.substringAfterLast('/')
            cited.filter { it.value == value }.maxByOrNull { like(it.member, key) }?.let { pointer to it.source }
        }.toMap()
    }

    private fun scalars(node: JsonElement, at: String): List<Pair<String, String>> = when (node) {
        is JsonObject -> node.entries.flatMap { (name, value) -> scalars(value, "$at/$name") }
        is JsonArray -> node.flatMapIndexed { i, value -> scalars(value, "$at/$i") }
        is JsonPrimitive -> listOf(at to node.content)
    }

    /** How alike two member names are: the length of what they begin with alike. */
    private fun like(a: String, b: String): Int = a.zip(b).takeWhile { (x, y) -> x == y }.size

    /** A member of an outcome kept by name, and its value. */
    private class Cited(val name: String, val member: String, val value: String) {
        val source: JsonObject = buildJsonObject {
            put("kept", name)
            put("pointer", "/$member")
        }
    }

    private companion object {
        val CITED = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_-]*)}""")
    }
}
