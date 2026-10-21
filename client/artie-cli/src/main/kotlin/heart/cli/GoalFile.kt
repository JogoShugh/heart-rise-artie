package heart.cli

import heart.script.Declared
import heart.script.Goal
import org.yaml.snakeyaml.Yaml
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A goal file, NAME.goal.yaml (spec/PLAN-PLAIN-TEXT-RUN.md, "The goal file"): `start`, the bookmark; `inputs`, each
 * as a script's input() declares it; `goal`, the task in words. A key it doesn't know stops the run, naming it, so a
 * file written for a later ARTIE never runs half-understood.
 */
fun goalFile(name: String, text: String): Goal {
    val doc = Yaml().load<Any?>(text) as? Map<*, *>
    val start = doc?.get("start")?.toString()
    val goal = doc?.get("goal")?.toString()?.takeIf { it.isNotBlank() }
    val problem = when {
        doc == null -> "not a goal file"
        (doc.keys.map { it.toString() } - KEYS).isNotEmpty() -> "no key ${(doc.keys.map { "$it" } - KEYS).first()}"
        start == null -> "start names the bookmark"
        goal == null -> "goal says what to do"
        else -> null
    }
    if (problem != null) throw BadArguments("$name: $problem")
    val inputs = (doc?.get("inputs") as? Map<*, *>).orEmpty().map { (input, value) -> declared(name, "$input", value) }
    return Goal(checkNotNull(start), inputs, checkNotNull(goal))
}

/**
 * One input as the file declares it: `name: value`, a default; `name:`, none; `name: { default, question }`; a
 * default `{ now: PATTERN }`, the time now as a java.time pattern.
 */
private fun declared(file: String, input: String, value: Any?): Declared {
    val given = value as? Map<*, *> ?: return Declared(input, value?.toString(), null)
    (given.keys.map { it.toString() } - INPUT_KEYS).firstOrNull()?.let {
        throw BadArguments("$file: input $input has no key $it")
    }
    val d = given["default"]
    val now = (d as? Map<*, *>)?.let {
        it["now"]?.toString() ?: throw BadArguments("$file: input $input's default is a value or { now: PATTERN }")
    }
    val default = now?.let { LocalDateTime.now().format(DateTimeFormatter.ofPattern(it)) } ?: d?.toString()
    return Declared(input, default, given["question"]?.toString(), now)
}

private val KEYS = setOf("start", "inputs", "goal")
private val INPUT_KEYS = setOf("default", "question")
