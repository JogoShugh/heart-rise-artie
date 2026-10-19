package heart.cli.generate

import heart.cli.BadArguments
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * `artie generate CAPTURE.yaml --out NEW.kts [--script SOURCE.kts]` (spec/PLAN-SCRIPT-GENERATOR.md): the script a
 * run's capture gives, and its lock file beside it, NEW.pins.yaml, both written in [folder].
 */
fun generate(args: List<String>, folder: Path, out: PrintStream) {
    val options = options(args)
    val captureName = args.getOrNull(1) ?: throw BadArguments(GENERATE_USAGE)
    val script = options["--out"] ?: throw BadArguments("--out names the script to write")
    val sourceName = options["--script"]
    val source = sourceName?.let { Files.readAllLines(existing(folder, it, "script")) }
    val pins = script.substringBeforeLast('.') + ".pins.yaml"
    val capture = Capture(captureName, Files.readString(existing(folder, captureName, "capture")))
    val writer = ScriptWriter(capture, source, sourceName, script, pins)
    val text = writer.write()
    val lock = LockWriter(capture)
    Files.writeString(folder.resolve(script), text)
    Files.writeString(folder.resolve(pins), lock.write(script, captureName))
    out.println("wrote $script: ${count(writer.reviews, "value")} to review")
    out.println("wrote $pins: ${count(lock.formCount, "form")} and ${count(lock.linkCount, "link")} pinned")
}

const val GENERATE_USAGE = "usage: artie generate <capture.yaml> --out <script.kts> [--script <source.kts>]"

private val VALUED = setOf("--out", "--script")

private fun options(args: List<String>): Map<String, String> {
    val rest = ArrayDeque(args.drop(2))
    val options = mutableMapOf<String, String>()
    while (rest.isNotEmpty()) {
        val option = rest.removeFirst()
        if (option !in VALUED) throw BadArguments("unknown option: $option")
        options[option] = rest.removeFirstOrNull() ?: throw BadArguments("$option needs a value")
    }
    return options
}

private fun existing(folder: Path, name: String, what: String): Path =
    folder.resolve(name).takeIf { Files.exists(it) } ?: throw BadArguments("no $what at $name")

private fun count(n: Int, thing: String) = if (n == 1) "1 $thing" else "$n ${thing}s"
