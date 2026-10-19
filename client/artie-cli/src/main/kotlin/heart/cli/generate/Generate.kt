package heart.cli.generate

import heart.cli.BadArguments
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * `artie generate CAPTURE.yaml --out NEW.kts [--script SOURCE.kts] [--keep-reason] [--inputs a,b]`
 * (spec/PLAN-SCRIPT-GENERATOR.md): the script a run's capture gives, and its lock file beside it, NEW.pins.yaml,
 * both written in [folder].
 */
fun generate(args: List<String>, folder: Path, out: PrintStream) {
    val options = options(args)
    val captureName = args.getOrNull(1) ?: throw BadArguments(GENERATE_USAGE)
    val script = options["--out"] ?: throw BadArguments("--out names the script to write")
    val sourceName = options["--script"]
    val source = sourceName?.let { Files.readAllLines(existing(folder, it, "script")) }
    val pins = script.substringBeforeLast('.') + ".pins.yaml"
    val capture = Capture(captureName, Files.readString(existing(folder, captureName, "capture")))
    val asInputs = options["--inputs"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        .associateWith { sentOnce(capture, it) }
    val chosen = ScriptWriter.Options(keepReason = "--keep-reason" in options, asInputs = asInputs)
    val writer = ScriptWriter(capture, source, sourceName, script, pins, chosen)
    val text = writer.write()
    val lock = LockWriter(capture)
    Files.writeString(folder.resolve(script), text)
    Files.writeString(folder.resolve(pins), lock.write(script, captureName))
    val kept = if (writer.kept > 0) ", ${count(writer.kept, "reason step")} kept" else ""
    out.println("wrote $script: ${count(writer.reviews, "value")} to review$kept")
    out.println("wrote $pins: ${count(lock.formCount, "form")} and ${count(lock.linkCount, "link")} pinned")
}

const val GENERATE_USAGE =
    "usage: artie generate <capture.yaml> --out <script.kts> [--script <source.kts>] [--keep-reason] [--inputs a,b]"

private val VALUED = setOf("--out", "--script", "--inputs")
private val FLAGS = setOf("--keep-reason")

private fun options(args: List<String>): Map<String, String> {
    val rest = ArrayDeque(args.drop(2))
    val options = mutableMapOf<String, String>()
    while (rest.isNotEmpty()) {
        val option = rest.removeFirst()
        if (option !in VALUED && option !in FLAGS) throw BadArguments("unknown option: $option")
        val value = if (option in FLAGS) "" else rest.removeFirstOrNull()
        options[option] = value ?: throw BadArguments("$option needs a value")
    }
    return options
}

/** The one text the run sent under the field [name], which `--inputs` makes an input's default. */
private fun sentOnce(capture: Capture, name: String): String {
    val sent = capture.steps.filter { it.relation != null }.mapNotNull { it.params[name]?.toString() }.distinct()
    val problem = when {
        name in capture.inputs -> "$name is an input already"
        sent.isEmpty() -> "the run sent no $name"
        sent.size > 1 -> "the run sent more than one $name"
        else -> null
    }
    return if (problem == null) sent.single() else throw BadArguments("--inputs $name: $problem")
}

private fun existing(folder: Path, name: String, what: String): Path =
    folder.resolve(name).takeIf { Files.exists(it) } ?: throw BadArguments("no $what at $name")

private fun count(n: Int, thing: String) = if (n == 1) "1 $thing" else "$n ${thing}s"
