package heart.cli

import heart.cli.generate.generate
import heart.script.ClientSettings
import heart.script.Navigator
import heart.script.Refusal
import heart.script.Run
import heart.script.ScriptStopped
import heart.script.TraceStep
import heart.script.test.ExpectationNotMet
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import heart.script.host.ScriptHost
import javax.script.ScriptException

/**
 * `artie run` (spec/PLAN-ARTIE-SCRIPTS.md, "Running a script"): a script from [folder], run with the
 * client [navigator] gives, one line per step to [out]; a person, only on --interactive, answers
 * through [typed]. The exit says how the run ended. [reportBuilder] is heart-run-report's
 * build-run-report.sh, installed beside the command: --report runs it on the trace.
 */
class ArtieCli(
    private val folder: Path,
    private val out: PrintStream,
    private val typed: () -> String?,
    /** The client, made as the run asks: the rung it pins, the reasoner it allows. */
    private val navigator: (ClientSettings) -> Navigator,
    private val reportBuilder: Path? = null,
) {
    fun run(args: List<String>): Int {
        if (args.firstOrNull() == "generate") return generated(args)
        val ended = try {
            val asked = arguments(args)
            val file = folder.resolve(asked.script)
            if (!Files.exists(file)) throw BadArguments("no script at ${asked.script}")
            if (asked.report != null && reportBuilder == null) throw BadArguments("--report: no report builder")
            run(asked, file)
        } catch (e: BadArguments) {
            Ended(STOPPED_BEFORE, "stopped", e.message.orEmpty(), 0)
        }
        report(ended)
        return ended.exit
    }

    private var output = "words"

    /** `artie generate`: a run's capture made a script with no model in it (spec/PLAN-SCRIPT-GENERATOR.md). */
    private fun generated(args: List<String>): Int = try {
        generate(args, folder, out, reportBuilder)
        0
    } catch (e: BadArguments) {
        out.println(e.message)
        STOPPED_BEFORE
    }

    private fun run(asked: Arguments, file: Path): Ended {
        output = asked.output
        val given = asked.inputsFile?.let { inputsFile(folder.resolve(it)) }.orEmpty() + asked.inputs
        val config = config(asked, folder, file)
        val lock = lockFile(asked, folder, file, config)
        val settings = ClientSettings(lock.rung, asked.reasoner, asked.exchanges, lock.pins, lock.guard)
        val run = Run(given, asked.interactive, ::ask, listing = asked.inputsOf, navigator = { navigator(settings) })
        run.reasoning = asked.reasoner != null
        run.entry = asked.entry
        if (output == "words") run.onStep = { out.println(line(run.trace.size, it)) }
        val text = Files.readString(file)
        // A script that reasons is known before it runs, by its text: without --reasoner, nothing is sent.
        if (!asked.inputsOf && asked.reasoner == null && Regex("""\breason\(""").containsMatchIn(text)) {
            throw BadArguments("stopped before the first request: reason(…) needs --reasoner")
        }
        val ended = execute(run, text)
        if (asked.inputsOf) return listed(run, text)
        val name = file.fileName.toString().substringBefore('.')
        // The run's capture, as a person reads it: YAML (spec/PLAN-LINE-BY-LINE-REPORT.md).
        val trace = folder.resolve(asked.trace ?: "runs/$name-${Instant.now().epochSecond}.trace.yaml")
        Files.createDirectories(trace.toAbsolutePath().parent)
        Files.writeString(trace, run.traceYaml())
        val repinned = repin(asked, folder, file, config, run.traceYaml())
        if (output == "words") repinned.forEach(out::println)
        asked.report?.let { report(folder.resolve(it), trace, file, run) }
        return ended
    }

    /**
     * The reports, in [dir], from heart-run-report's builders, given the trace and the script: the line-by-line
     * report as index.html (spec/PLAN-LINE-BY-LINE-REPORT.md: the script first, then what each line did), and
     * the run report, its tiles and rows, as run.html.
     */
    private fun report(dir: Path, trace: Path, script: Path, run: Run) {
        Files.createDirectories(dir)
        val entry = run.trace.firstOrNull()?.asked?.params?.get("entry")
        val lede = "${script.fileName}, run with artie run" + (entry?.let { " from $it" } ?: "") + "."
        val title = script.fileName.toString().substringBefore('.')
        val builder = checkNotNull(reportBuilder)
        val lines = builder.resolveSibling("build-script-report.sh")
        val built = listOf(
            build(lines, trace, dir.resolve("index.html"), title, lede, script),
            build(builder, trace, dir.resolve("run.html"), title, lede, script),
        )
        val failed = built.filterNotNull()
        if (failed.isNotEmpty()) {
            out.println("the report failed: ${failed.joinToString("; ")}")
        } else if (output == "words") {
            out.println("report: ${folder.relativize(dir.resolve("index.html").toAbsolutePath().normalize())}")
        }
    }

    /** One page from [builder]; null when it was built, else what the builder said. */
    @Suppress("LongParameterList") // A builder's own arguments, each once, in its order.
    private fun build(builder: Path, trace: Path, page: Path, title: String, lede: String, script: Path): String? {
        val process = ProcessBuilder("sh", "$builder", "$trace", "$page", title, lede, "$script")
            .redirectErrorStream(true).start()
        val said = process.inputStream.bufferedReader().readText()
        return if (process.waitFor() == 0) null else said.trim()
    }

    private fun execute(run: Run, script: String): Ended {
        // A reason step's template is read from the script's own text.
        run.source = script
        Run.current = run
        val engine = ScriptHost.engine(IMPORTS)
        return try {
            engine.eval(script)
            Ended(0, "finished", "finished, ${run.trace.size} steps", run.trace.size)
        } catch (e: ScriptException) {
            stopIn(e)?.let { stopped(it, run) }
                ?: Ended(SCRIPT_ERROR, "failed", "the script failed: ${(e.cause ?: e).message}", run.trace.size)
        } catch (e: ScriptStopped) {
            stopped(e, run)
        }
    }

    private fun stopped(stop: ScriptStopped, run: Run): Ended {
        val kind = stop.refused?.kind
        val exit = when {
            stop is ExpectationNotMet -> NOT_MET
            kind in CLIENT_FAILED -> CLIENT_FAILURE
            kind == Refusal.NoBinding -> STOPPED_BEFORE
            kind != null -> REFUSED
            stop.step == 0 -> STOPPED_BEFORE
            else -> NOT_MET
        }
        val drawing = run.trace.lastOrNull()?.asked?.params?.get("drawing") as? String
        return Ended(exit, "stopped", stop.message.orEmpty(), run.trace.size, drawing)
    }

    /**
     * The declared inputs, and how many questions the script asks inline. A listing never runs the script's
     * body, so those are counted from its text: each `ask(` call, a comment's included.
     */
    private fun listed(run: Run, text: String): Ended {
        val inline = Regex("""\bask\(""").findAll(text).count()
        run.declared.forEach { input ->
            out.println(
                when (input.kind) {
                    "optional" -> "${input.name}: optional, default \"${input.default}\""
                    "question" -> "${input.name}: a question, \"${input.question}\""
                    else -> "${input.name}: required"
                },
            )
        }
        if (inline > 0) {
            val questions = if (inline == 1) "question" else "questions"
            out.println("$inline inline $questions, each answered by its default unattended")
        }
        return Ended(0, "listed", "", 0)
    }

    private fun report(ended: Ended) {
        when {
            ended.ended == "listed" -> Unit
            output == "json" -> out.println(
                buildJsonObject {
                    put("ended", ended.ended)
                    put("exit", ended.exit)
                    put("steps", ended.steps)
                    if (ended.exit != 0) put("line", ended.line)
                },
            )
            else -> {
                ended.drawing?.let(out::println)
                out.println(ended.line)
            }
        }
    }

    private fun ask(question: String, default: String?): String {
        out.print(if (default == null) "$question " else "$question [$default] ")
        return typed().orEmpty().ifEmpty { default ?: throw BadArguments("no answer to: $question") }
    }

    /** How a run ended; [drawing], a failed check's condition drawn with its values, printed above [line]. */
    private class Ended(val exit: Int, val ended: String, val line: String, val steps: Int, val drawing: String? = null)

    private companion object {
        val IMPORTS = listOf("heart.script.*", "heart.script.test.*")
        val CLIENT_FAILED = setOf(Refusal.Unreachable, Refusal.IntegrityMismatch)
        const val NOT_MET = 1
        const val STOPPED_BEFORE = 2
        const val REFUSED = 3
        const val CLIENT_FAILURE = 4
        const val SCRIPT_ERROR = 5
    }
}

/** The stop a script engine's failure carries, if a stop is what failed it. */
private fun stopIn(failure: Throwable): ScriptStopped? =
    generateSequence(failure.cause) { it.cause }.filterIsInstance<ScriptStopped>().firstOrNull()

/** One step, as a line: its number, what was asked with its values, and the outcome. */
internal fun line(number: Int, step: TraceStep): String {
    val said = Regex("\\{(\\w+)}").replace(step.asked.say) { step.asked.params[it.groupValues[1]].toString() }
    return "$number $said: ${step.outcome}"
}
