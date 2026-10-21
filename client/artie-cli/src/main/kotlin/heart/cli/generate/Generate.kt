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
fun generate(args: List<String>, folder: Path, out: PrintStream, builder: Path? = null) {
    val options = options(args)
    val captureName = args.getOrNull(1) ?: throw BadArguments(GENERATE_USAGE)
    val script = options["--out"] ?: throw BadArguments("--out names the script to write")
    val sourceName = options["--script"]
    val report = options["--report"]
    asked(report == null || sourceName != null) {
        "--report needs --script: the report shows the run's script beside the one generated"
    }
    val pins = script.substringBeforeLast('.') + ".pins.yaml"
    val capture = Capture(captureName, Files.readString(existing(folder, captureName, "capture")))
    // A goal run's steps stand for the lines of the script its moves amount to, which its capture keeps.
    val source = sourceName?.let { existing(folder, it, "script") }?.let { file ->
        capture.source?.takeIf { heart.cli.isGoal(file) }?.lines() ?: Files.readAllLines(file)
    }
    val asInputs = options["--inputs"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        .associateWith { sentOnce(capture, it) }
    val chosen = ScriptWriter.Options(keepReason = "--keep-reason" in options, asInputs = asInputs)
    val writer = ScriptWriter(capture, source, sourceName, script, pins, chosen)
    val text = writer.write()
    val lock = LockWriter(capture)
    val derivation = DerivationWriter(writer.derivations)
    val derived = script.substringBeforeLast('.') + ".derivation.yaml"
    Files.writeString(folder.resolve(script), text)
    Files.writeString(folder.resolve(pins), lock.write(script, captureName))
    Files.writeString(folder.resolve(derived), derivation.write(script, sourceName, captureName))
    val kept = if (writer.kept > 0) ", ${count(writer.kept, "reason step")} kept" else ""
    out.println("wrote $script: ${count(writer.reviews, "value")} to review$kept")
    out.println("wrote $pins: ${count(lock.formCount, "form")} and ${count(lock.linkCount, "link")} pinned")
    out.println("wrote $derived: ${count(derivation.count, "derivation")}")
    if (report != null && sourceName != null) {
        val page = "$report/index.html"
        derivationReport(builder, folder, page, listOf(sourceName, script, derived, pins), captureName)
        out.println("wrote $page")
    }
}

/**
 * The derivation report, from heart-run-report's builder beside [builder]: the run's script beside the generated
 * one, each derivation numbered on both sides, then the lock file and the derivation file, as [page]. [files] are
 * the source script, the generated one, the derivation file and the lock file, in [folder].
 */
private fun derivationReport(builder: Path?, folder: Path, page: String, files: List<String>, captureName: String) {
    val build = builder?.resolveSibling("build-derivation-report.sh")?.takeIf { Files.exists(it) }
        ?: throw BadArguments("--report: no report builder")
    val (source, script, derived) = files
    val pins = files.last()
    Files.createDirectories(folder.resolve(page).parent)
    val title = "From $source to $script"
    val lede = "$script, which artie generate wrote from $captureName, the run of $source, beside it: each number " +
        "marks one derivation on both sides, and its note says which rule bound it."
    val paths = listOf(source, script, derived, page).map { "${folder.resolve(it)}" }
    val beside = listOf(pins, derived).map { "${folder.resolve(it)}" }
    val process = ProcessBuilder(listOf("sh", "$build") + paths + listOf(title, lede) + beside)
        .redirectErrorStream(true).start()
    val said = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) throw BadArguments("the report failed: ${said.trim()}")
}

const val GENERATE_USAGE =
    "usage: artie generate <capture.yaml> --out <script.kts> [--script <source.kts>] [--keep-reason] [--inputs a,b]" +
        " [--report <dir>]"

private val VALUED = setOf("--out", "--script", "--inputs", "--report")
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

/** Stops when the arguments ask for what can't be done, saying why. */
private fun asked(possible: Boolean, why: () -> String) {
    if (!possible) throw BadArguments(why())
}

private fun existing(folder: Path, name: String, what: String): Path =
    folder.resolve(name).takeIf { Files.exists(it) } ?: throw BadArguments("no $what at $name")

private fun count(n: Int, thing: String) = if (n == 1) "1 $thing" else "$n ${thing}s"
