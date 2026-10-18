package heart.cli

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path

/** What `artie run` was asked: the script, its inputs, and the options (spec/PLAN-ARTIE-SCRIPTS.md). */
class Arguments(val script: String) {
    val inputs = mutableMapOf<String, String>()
    var inputsFile: String? = null
    var inputsOf = false
    var interactive = false
    var entry: String? = null
    var trace: String? = null
    var report: String? = null
    var rung: String? = null
    var reasoner: String? = null

    /** words, quiet or json: how the run reports itself. */
    var output = "words"
}

/** Arguments that can't be run, with the line that says why. */
class BadArguments(message: String) : RuntimeException(message)

const val USAGE = "usage: artie run <script> [name=value …] [options]"

/** The rungs a run may pin (SPEC §6.10). */
private val RUNGS = setOf("L0", "L1", "L2")

/** The reasoners a run may allow `reason(…)` steps to use. */
private val REASONERS = setOf("claude")

/** The options that stand alone. */
private val FLAGS: Map<String, (Arguments) -> Unit> = mapOf(
    "--inputs-of" to { it.inputsOf = true },
    "--interactive" to { it.interactive = true },
    "--quiet" to { it.output = "quiet" },
)

/** The options that take a value. */
private val VALUED: Map<String, (Arguments, String) -> Unit> = mapOf(
    "--inputs" to { a, v -> a.inputsFile = v },
    "--entry" to { a, v -> a.entry = v },
    "--trace" to { a, v -> a.trace = v },
    "--report" to { a, v -> a.report = v },
    "--rung" to { a, v -> a.rung = v.takeIf { it in RUNGS } ?: throw BadArguments("--rung is L0, L1 or L2") },
    "--reasoner" to { a, v -> a.reasoner = v.takeIf { it in REASONERS } ?: throw BadArguments("--reasoner is claude") },
    "--output" to { a, v -> a.output = v.takeIf { it == "json" } ?: throw BadArguments("--output json only") },
)

/** Reads `run <script> [name=value …] [options]`; [BadArguments] when they can't be run. */
fun arguments(args: List<String>): Arguments {
    if (args.size < 2 || args[0] != "run") throw BadArguments(USAGE)
    val parsed = Arguments(args[1])
    val rest = ArrayDeque(args.drop(2))
    while (rest.isNotEmpty()) option(parsed, rest.removeFirst(), rest)
    return parsed
}

private fun option(parsed: Arguments, arg: String, rest: ArrayDeque<String>) {
    val flag = FLAGS[arg]
    val valued = VALUED[arg]
    when {
        !arg.startsWith("--") && '=' in arg -> parsed.inputs[arg.substringBefore('=')] = arg.substringAfter('=')
        flag != null -> flag(parsed)
        valued != null -> valued(parsed, rest.removeFirstOrNull() ?: throw BadArguments("$arg needs a value"))
        else -> throw BadArguments("unknown option: $arg")
    }
}

/** An inputs file's values, JSON or YAML by its extension; every value as text. */
fun inputsFile(file: Path): Map<String, String> {
    if (!Files.exists(file)) throw BadArguments("no inputs file at ${file.fileName}")
    val text = Files.readString(file)
    return when (file.fileName.toString().substringAfterLast('.')) {
        "json" -> (Json.parseToJsonElement(text) as JsonObject)
            .mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }
        "yaml", "yml" -> Yaml().load<Map<String, Any?>>(text).orEmpty().mapValues { (_, v) -> v.toString() }
        else -> throw BadArguments("inputs are JSON or YAML: ${file.fileName}")
    }
}
