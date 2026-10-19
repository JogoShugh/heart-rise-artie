package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where each value a step sent came from (spec/PLAN-LINE-BY-LINE-REPORT.md, "One capture, two uses"), so a
 * script generator can tell an input from a value read from an earlier answer, and both from a constant.
 * Every source that fits is named, in this order: an input with that value; a member of an earlier step's
 * state, the nearest step first, by JSON Pointer; the reason step's words. A value no whole source fits names
 * its parts that do; one with none is a constant (or, on a reason step, the reasoner's own choice).
 */
internal object Sources {
    /** The members of a step's values that name what was selected, not what was sent. */
    private val SELECTION = setOf("relation", "entry", "address")

    fun values(step: TraceStep, run: Run, earlier: List<TraceStep>): JsonObject = buildJsonObject {
        val words = step.asked.say.takeIf { step.asked.mode == "intent" }
        step.asked.params.filterKeys { it !in SELECTION }.forEach { (name, value) ->
            put(name, buildJsonObject {
                put("value", element(value))
                put("from", JsonArray(of(text(value), run, earlier, words)))
            })
        }
    }

    /**
     * A reason step's template, the reason(…) literal on its [line] of [source] as the script wrote it, and each
     * slot's value, read by matching the template against the [words], with its source.
     */
    fun reason(source: String?, line: Int?, words: String, run: Run, earlier: List<TraceStep>): JsonObject? {
        val written = line?.let { source?.lines()?.getOrNull(it - 1) }
        val template = written?.let(Template::of) ?: return null
        val values = template.match(words) ?: emptyMap()
        return buildJsonObject {
            put("template", template.text)
            put("slots", buildJsonObject {
                values.forEach { (slot, value) ->
                    put(slot, buildJsonObject {
                        put("value", value)
                        val input = slot.takeIf { name -> run.declared.any { it.name == name } }
                        val from = input?.let { listOf(input(it)) } ?: of(value, run, earlier, words = null)
                        put("from", JsonArray(from))
                    })
                }
            })
        }
    }

    /** Every source of [value]: inputs, earlier states, the [words]; else its parts; else a constant or a choice. */
    private fun of(value: String, run: Run, earlier: List<TraceStep>, words: String?): List<JsonElement> {
        val none = listOf(JsonPrimitive(if (words != null) "chosen" else "constant"))
        val whole = inputs(run).filter { it.second == value }.map { input(it.first) } +
            states(earlier).filter { it.value == value }.map { it.source } +
            listOfNotNull(JsonPrimitive("words").takeIf { words != null && word(value).containsMatchIn(words) })
        val parts = (inputs(run).map { it.second to input(it.first) } + states(earlier).map { it.value to it.source })
            .filter { (part, _) -> part.isNotEmpty() && part != value && word(part).containsMatchIn(value) }
            .distinctBy { it.first }
        return when {
            value.isEmpty() -> none
            whole.isNotEmpty() -> whole.distinct()
            parts.isNotEmpty() -> listOf(buildJsonObject { put("parts", JsonObject(parts.toMap())) })
            else -> none
        }
    }

    private fun inputs(run: Run): List<Pair<String, String>> = run.inputsUsed.map { (name, used) -> name to used.first }

    private fun input(name: String): JsonObject = buildJsonObject { put("input", name) }

    /** Each scalar of each earlier step's state, the nearest step first, with where it is. */
    private fun states(earlier: List<TraceStep>): List<Held> =
        earlier.withIndex().reversed().flatMap { (i, step) ->
            step.now?.let { scalars(it.state, "").map { (pointer, value) -> Held(value, i + 1, pointer) } }.orEmpty()
        }

    private fun scalars(node: JsonElement, at: String): List<Pair<String, String>> = when (node) {
        is JsonObject -> node.entries.flatMap { (name, value) -> scalars(value, "$at/${escape(name)}") }
        is JsonArray -> node.flatMapIndexed { i, value -> scalars(value, "$at/$i") }
        is JsonPrimitive -> listOf(at to node.content)
    }

    private fun escape(name: String) = name.replace("~", "~0").replace("/", "~1")

    /** [part] as a whole word: not inside a longer word or number. */
    private fun word(part: String) = Regex("(?<![\\w])" + Regex.escape(part) + "(?![\\w])")

    private fun text(value: Any?): String = when (value) {
        null -> ""
        is JsonPrimitive -> value.content
        else -> value.toString()
    }

    /** One scalar an earlier step held, and where: that step, by JSON Pointer. */
    private class Held(val value: String, step: Int, pointer: String) {
        val source: JsonObject = buildJsonObject {
            put("step", step)
            put("pointer", pointer)
        }
    }
}

/**
 * The words of a reason(…) call as the script wrote them, before Kotlin filled them in: plain text and slots,
 * `$name` or `${expression}`. Matching it against the words the run said gives each slot's value.
 */
internal class Template private constructor(val text: String, private val parts: List<Pair<Boolean, String>>) {

    /** Each slot's value in [words], by its name or expression; null when the words don't fit the template. */
    fun match(words: String): Map<String, String>? {
        val pattern = parts.joinToString("") { (slot, piece) -> if (slot) "(.+?)" else Regex.escape(piece) }
        val found = Regex("^$pattern$").matchEntire(words) ?: return null
        val slots = parts.filter { it.first }.map { it.second }
        return slots.zip(found.groupValues.drop(1)).toMap()
    }

    companion object {
        private const val CALL = "reason(\""

        /** The template of the reason(…) call on [line], or null when it has none. */
        fun of(line: String): Template? {
            val start = line.indexOf(CALL).takeIf { it >= 0 }?.plus(CALL.length) ?: return null
            val parts = mutableListOf<Pair<Boolean, String>>()
            val plain = StringBuilder()
            var i = start
            while (i < line.length && line[i] != '"') {
                when {
                    line[i] == '\\' && i + 1 < line.length -> { plain.append(line[i + 1]); i += 2 }
                    line.startsWith("\${", i) -> {
                        val end = closing(line, i + 2)
                        parts.flush(plain)
                        parts += true to line.substring(i + 2, end)
                        i = end + 1
                    }
                    line[i] == '$' && i + 1 < line.length && line[i + 1].isJavaIdentifierStart() -> {
                        var j = i + 1
                        while (j < line.length && line[j].isJavaIdentifierPart()) j++
                        parts.flush(plain)
                        parts += true to line.substring(i + 1, j)
                        i = j
                    }
                    else -> { plain.append(line[i]); i++ }
                }
            }
            parts.flush(plain)
            return Template(line.substring(start, i), parts)
        }

        private fun MutableList<Pair<Boolean, String>>.flush(plain: StringBuilder) {
            if (plain.isNotEmpty()) add(false to plain.toString())
            plain.clear()
        }

        /** Where the `${` expression that begins at [from] ends: its matching `}`, strings inside it skipped. */
        private fun closing(line: String, from: Int): Int {
            var depth = 1
            var i = from
            var quoted = false
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '\\' -> i++
                    c == '"' -> quoted = !quoted
                    !quoted && c == '{' -> depth++
                    !quoted && c == '}' -> if (--depth == 0) return i
                }
                i++
            }
            return line.length
        }
    }
}
