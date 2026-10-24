package heart.cli.goal

import heart.cli.BadArguments
import heart.script.Declared
import heart.script.Limits
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A goal file's front matter (spec/PLAN-GOAL-MARKDOWN.md): `start`, the bookmark; `inputs`, each as a script's
 * input() declares it; `limits`. Its keys ignore case; the names of its inputs keep it. [end] is the index of its
 * closing `---`, where the body begins after.
 */
internal class FrontMatter(
    val start: String,
    val inputs: List<Declared>,
    val limits: Limits,
    val end: Int,
    /** `manners`: the run's manners by name; null for none. */
    val manners: String? = null,
)

/** The front matter of [file], whose [lines] are the whole file; a rule it breaks stops the run, at its line. */
internal fun frontMatter(file: String, lines: List<String>): FrontMatter {
    val opens = "a goal file begins with its front matter, between --- lines"
    if (lines.firstOrNull()?.trim() != "---") fail(file, 1, opens)
    val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: fail(file, 1, opens)
    val yaml = lines.subList(1, end)
    val at = { key: String, from: Int -> lineOf(yaml, key, from) }
    val doc = try {
        Yaml().load<Any?>(yaml.joinToString("\n"))
    } catch (e: YAMLException) {
        fail(file, 1, "the front matter is not YAML: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
    }
    val map = doc as? Map<*, *> ?: if (doc == null) emptyMap<Any?, Any?>() else fail(file, 1, "front matter is keys")
    val keys = keyed(map, KEYS) { key, what -> fail(file, at(key, 0), what) }
    val start = keys["start"]?.toString()?.takeIf { it.isNotBlank() } ?: fail(file, 1, "start names the bookmark")
    val inputs = inputs(file, keys["inputs"], at)
    val limits = limits(file, keys["limits"], at("limits", 0))
    val manners = keys["manners"]?.toString()?.also {
        if (it !in MANNERS) fail(file, at("manners", 0), "manners: ${MANNERS.joinToString()} is the one there is")
    }
    return FrontMatter(start, inputs, limits, end, manners)
}

private fun inputs(file: String, given: Any?, at: (String, Int) -> Int): List<Declared> {
    val inputs = (given as? Map<*, *>).orEmpty()
    val seen = mutableListOf<String>()
    return inputs.map { (key, value) ->
        val name = key.toString()
        seen.firstOrNull { it.equals(name, ignoreCase = true) }?.let {
            fail(file, at(name, 0), "$name differs from $it only in case")
        }
        seen += name
        declared(name, value) { what -> fail(file, at(name, 0), what) }
    }
}

/**
 * One input as the file declares it: `name: value`, a default; `name:`, none; `name: { default, question }`; a
 * default `{ now: PATTERN }`, the time now as a java.time pattern, whose case is its meaning.
 */
private fun declared(name: String, value: Any?, fail: (String) -> Nothing): Declared {
    val given = value as? Map<*, *> ?: return Declared(name, value?.toString(), null)
    val keys = keyed(given, INPUT_KEYS) { key, _ -> fail("input $name has no key $key") }
    val default = keys["default"]
    val now = (default as? Map<*, *>)?.let { pattern ->
        keyed(pattern, setOf("now")) { _, _ -> fail("input $name's default is a value or { now: PATTERN }") }["now"]
            ?.toString() ?: fail("input $name's default is a value or { now: PATTERN }")
    }
    val value = now?.let { LocalDateTime.now().format(DateTimeFormatter.ofPattern(it)) } ?: default?.toString()
    return Declared(name, value, keys["question"]?.toString(), now)
}

private fun limits(file: String, given: Any?, line: Int): Limits {
    val keys = keyed((given as? Map<*, *>).orEmpty(), setOf("moves", "step")) { key, _ ->
        fail(file, line, "limits has no key $key: it has moves and step")
    }
    fun limit(key: String, default: Int): Int {
        val value = keys[key] ?: return default
        return (value as? Int)?.takeIf { it > 0 } ?: fail(file, line, "limits: $key is a whole number above 0")
    }
    val defaults = Limits()
    return Limits(limit("moves", defaults.moves), limit("step", defaults.step))
}

/**
 * [map]'s entries by key, each key in lower case, as the file's keywords ignore case: a key not [known], or one
 * given twice in two cases, is told to [refuse] with the key as written.
 */
private fun keyed(map: Map<*, *>, known: Set<String>, refuse: (String, String) -> Nothing): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    val spelled = mutableMapOf<String, String>()
    map.forEach { (k, v) ->
        val key = k.toString()
        val lower = key.lowercase()
        if (lower !in known) refuse(key, "no key $key: the front matter has start, inputs, limits and manners")
        spelled[lower]?.let { refuse(key, "$key is $it again") }
        spelled[lower] = key
        out[lower] = v
    }
    return out
}

/** The line in the file of [key] in the front matter's [yaml] lines, looked for from [from]; 1 when not found. */
private fun lineOf(yaml: List<String>, key: String, from: Int): Int {
    val pattern = Regex("^\\s*${Regex.escape(key)}\\s*:")
    val at = (from until yaml.size).lastOrNull { pattern.containsMatchIn(yaml[it]) } ?: return 1
    // The front matter begins on the file's second line.
    return at + 2
}

/** Stops the run before its first request: [what] is wrong at [line] of [file]. */
internal fun fail(file: String, line: Int, what: String): Nothing = throw BadArguments("$file:$line: $what")

private val KEYS = setOf("start", "inputs", "limits", "manners")

/** The manners a goal may name (spec/ideas/goals-that-work-alongside-others.md). */
private val MANNERS = listOf("alongside-others")
private val INPUT_KEYS = setOf("default", "question")
