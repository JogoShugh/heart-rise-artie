package heart.cli

import heart.script.Answer
import heart.script.Refusal
import heart.script.ScriptSteps
import heart.script.Side
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** run.feature: artie run, in a folder of its own, against the DSL features' scripted service. */
class CliSteps(private val script: ScriptSteps) {
    private val folder = Files.createTempDirectory("artie-run")
    private val typed = ArrayDeque<String>()
    private var exit = -1
    private var printed = listOf<String>()
    private var askedRung: String? = null
    private var askedReasoner: String? = null
    private var askedExchanges: Boolean? = null
    private var askedPins: heart.script.Pins? = null
    private var askedGuard: String? = null
    private var askedValidators: String? = null

    @Given("the client cannot reach the server for {string} at {string}: {string}")
    fun unreachable(rel: String, at: String, detail: String) {
        script.service.refusals["$rel at $at"] = Answer.Refused(Refusal.Unreachable, Side.CLIENT, detail)
    }

    @Given("the script {string} is")
    fun scriptIs(name: String, text: String) {
        Files.writeString(folder.resolve(name), text)
    }

    @Given("the file {string} is")
    fun fileIs(name: String, text: String) {
        Files.writeString(folder.resolve(name), text)
    }

    /**
     * Line [number] of [name] put as [text], [indent] spaces before it, each `\n` in it a line break: one file,
     * broken one way. A table trims its cells, so the indent is its own column.
     */
    @Given("line {int} of {string} reads {string}, indented {int}")
    fun lineReads(number: Int, name: String, text: String, indent: Int) {
        val lines = Files.readAllLines(folder.resolve(name))
        lines[number - 1] = " ".repeat(indent) + text.replace("\\n", "\n")
        Files.writeString(folder.resolve(name), lines.joinToString("\n") + "\n")
    }

    @Given("the planner will move")
    fun plannerMoves(table: DataTable) = table.asMaps().forEach { row ->
        val sentence = row["sentence"]?.takeIf { it.isNotBlank() }?.toInt()
        script.service.moves += when (val kind = row.getValue("move")) {
            "act" -> heart.script.Move.Act(row.getValue("words"), sentence, row["opens"] == "yes")
            "done" -> heart.script.Move.Done(checkNotNull(sentence))
            "finish" -> heart.script.Move.Finish
            else -> error("no move $kind")
        }
    }

    @Given("the client has no planner, because {string}")
    fun noPlanner(why: String) {
        script.service.missingPlanner = why
    }

    @Given("the client counted these model tokens")
    fun counted(table: DataTable) {
        script.service.counted = table.asMaps().single().mapValues { it.value.toLong() }
    }

    @Then("the trace {string} counts these model tokens")
    fun traceCounts(name: String, table: DataTable) {
        val head = org.yaml.snakeyaml.Yaml().loadAll(Files.readString(folder.resolve(name))).first() as Map<*, *>
        val usage = ((head["run"] as Map<*, *>)["usage"] as Map<*, *>).mapKeys { "${it.key}" }
            .mapValues { (it.value as Number).toLong() }
        assertEquals(table.asMaps().single().mapValues { it.value.toLong() }, usage)
    }

    @Then("the file {string} has these lines, in order")
    fun hasLinesInOrder(name: String, table: DataTable) {
        val lines = Files.readAllLines(folder.resolve(name)).map { it.trim() }
        val found = table.asMaps().map { it.getValue("line") }.map { line ->
            lines.indexOf(line).also { assertTrue(it >= 0, "no \"$line\" in:\n${lines.joinToString("\n")}") }
        }
        assertEquals(found.sorted(), found, lines.joinToString("\n"))
    }

    @Then("the file {string} has {int} line(s) that read(s) {string}")
    fun hasCount(name: String, count: Int, line: String) =
        assertEquals(count, Files.readAllLines(folder.resolve(name)).count { it.trim() == line })

    @Then("the file {string} has these lines")
    fun hasLines(name: String, table: DataTable) {
        val lines = Files.readAllLines(folder.resolve(name)).map { it.trim() }
        table.asMaps().map { it.getValue("line") }.forEach { assertTrue(it in lines, "no \"$it\" in:\n$lines") }
    }

    @Given("the person will type")
    fun willType(table: DataTable) = table.asMaps().forEach { typed.addLast(it.getValue("line")) }

    @When("artie is run with")
    fun artieRuns(table: DataTable) {
        val bytes = ByteArrayOutputStream()
        val args = table.asMaps().map { it["argument"].orEmpty() }.filter { it.isNotEmpty() }
        val client = { settings: heart.script.ClientSettings ->
            askedRung = settings.rung.orEmpty()
            askedReasoner = settings.reasoner.orEmpty()
            askedExchanges = settings.exchanges
            askedPins = settings.pins
            askedGuard = settings.guarding.guard
            askedValidators = settings.guarding.validators
            made++
            script.service
        }
        val cli = ArtieCli(folder, PrintStream(bytes, true), { typed.removeFirstOrNull() }, client, BUILDER)
        exit = cli.run(args)
        printed = bytes.toString().lines().filter { it.isNotBlank() }
    }

    /**
     * The process told to stop, as a guard's kill tells it: the run's own handling never sees it, as a signal ends
     * the process around it. The shutdown hooks a JVM would run are run here, then the run is broken off.
     */
    @Given("the process is told to stop when the client acts {string}, its trace {string} kept as {string}")
    fun toldToStop(relation: String, trace: String, kept: String) {
        script.service.onAct[relation] = {
            ShutdownHooks.runAll()
            // What a stopped process leaves: the trace as it was then, whatever this one, going on, writes after.
            folder.resolve(trace).takeIf { Files.exists(it) }?.let { Files.copy(it, folder.resolve(kept)) }
            throw ToldToStop()
        }
    }

    /** What a signal is to the run: nothing it catches. */
    private class ToldToStop : Error("told to stop")

    /** The JVM's shutdown hooks, run as a signal runs them, though the process goes on (needs java.lang opened). */
    private object ShutdownHooks {
        fun runAll() {
            val hooks = Class.forName("java.lang.ApplicationShutdownHooks").getDeclaredField("hooks")
            hooks.isAccessible = true
            (hooks.get(null) as Map<*, *>).keys.toList().forEach { (it as Thread).run() }
        }
    }

    @Then("artie exits {int}")
    fun exits(code: Int) = assertEquals(code, exit, printed.joinToString("\n"))

    @Then("artie prints")
    fun prints(table: DataTable) = assertEquals(table.asMaps().map { it.getValue("line") }, printed)

    @Then("artie prints, exactly")
    fun printsExactly(lines: String) = assertEquals(lines, printed.joinToString("\n"))

    /** How many clients the run asked for: one for the script, one more for each listener. */
    private var made = 0

    @Then("the client was made {int} times")
    fun madeTimes(times: Int) = assertEquals(times, made)

    @Then("the client was made to ask at rung {string}")
    fun rung(rung: String) = assertEquals(rung, askedRung.orEmpty())

    @Then("the client was made to keep its exchanges")
    fun keepsExchanges() = assertEquals(true, askedExchanges)

    @Then("the client was made not to keep its exchanges")
    fun keepsNoExchanges() = assertEquals(false, askedExchanges)

    @Then("the client was made holding the lock file pinning {string} to {string}, {string}")
    fun holdsLock(rel: String, integrity: String, guard: String) {
        assertEquals(integrity, askedPins?.forms?.get(rel)?.integrity)
        assertEquals(guard, askedGuard)
    }

    @Then("the file {string} reads")
    fun fileReads(name: String, text: String) = assertEquals(text, Files.readString(folder.resolve(name)).trimEnd())

    /** Each selection a trace made, in order: its relation and the values it sent, as text. */
    private fun selections(trace: String): List<String> =
        org.yaml.snakeyaml.Yaml().loadAll(Files.readString(folder.resolve(trace))).toList().drop(1)
            .map { it as Map<*, *> }
            .mapNotNull { step -> (step["params"] as? Map<*, *>)?.takeIf { "relation" in it } }
            .map { params -> params.entries.joinToString(", ") { (k, v) -> "$k=$v" } }

    @Then("the run traced in {string} made the same selections as the run traced in {string}")
    fun sameSelections(made: String, from: String) = assertEquals(selections(from), selections(made))

    /** A derivation file, read as YAML. */
    private fun derivation(name: String): Map<*, *> =
        org.yaml.snakeyaml.Yaml().load<Any?>(Files.readString(folder.resolve(name))) as Map<*, *>

    /** Spans as a table writes them: `line:column text`, joined by "; ". */
    private fun spans(spans: Any?): String = (spans as? List<*>).orEmpty().map { it as Map<*, *> }
        .joinToString("; ") { "${it["line"]}:${it["column"]} ${it["text"]}" }

    @Then("the derivation {string} has these derivations")
    fun derivations(name: String, table: DataTable) {
        val derivations = (derivation(name)["derivations"] as List<*>).map { it as Map<*, *> }
        val found = derivations.map {
            mapOf(
                "n" to it["n"].toString(), "kind" to it["kind"].toString(), "rule" to it["rule"].toString(),
                "title" to it["title"].toString(), "source" to spans(it["source"]),
                "generated" to spans(it["generated"]),
            )
        }
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value.orEmpty() } }, found)
        assertTrue(derivations.all { !(it["why"] as? String).isNullOrBlank() }, "each derivation says why")
    }

    @Then("the derivation {string} lines up these rows")
    fun rows(name: String, table: DataTable) {
        val found = (derivation(name)["rows"] as List<*>).map { it as Map<*, *> }
            .map { mapOf("at" to it["at"].toString(), "source" to it["source"].toString(),
                "generated" to it["generated"].toString()) }
        assertEquals(table.asMaps(), found)
    }

    @Then("the derivation report {string} shows {string} beside {string}, by {string}")
    fun derivationReport(name: String, source: String, generated: String, derivation: String) {
        val page = Files.readString(folder.resolve(name))
        fun embedded(id: String) = Regex("""id="$id">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL).find(page)
            ?.groupValues?.get(1)?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")?.trimEnd('\n')
        assertEquals(Files.readString(folder.resolve(source)).trimEnd('\n'), embedded("source-text"), "the run's")
        assertEquals(Files.readString(folder.resolve(generated)).trimEnd('\n'), embedded("generated-text"), "made")
        assertEquals(Files.readString(folder.resolve(derivation)).trimEnd('\n'), embedded("derivation"), "how")
    }

    @Then("after the two scripts, the derivation report {string} shows these files, in order")
    fun derivationReportFiles(name: String, table: DataTable) {
        val page = Files.readString(folder.resolve(name))
        val files = Regex("""<pre hidden class="beside" data-name="([^"]*)">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(page).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(table.asMaps().map { it.getValue("file") }, files.map { it.first })
        files.forEach { (file, shown) ->
            val text = shown.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trimEnd('\n')
            assertEquals(Files.readString(folder.resolve(file)).trimEnd('\n'), text, file)
        }
        assertTrue(page.indexOf("id=\"generated-text\"") < page.indexOf("class=\"beside\""), "the scripts come first")
    }

    @Then("the client was made holding no lock file")
    fun holdsNoLock() {
        assertEquals(null, askedPins)
        assertEquals(null, askedGuard)
    }

    @Then("the client was made holding no lock file, off the rails")
    fun holdsNoLockOffRails() {
        assertEquals(null, askedPins)
        assertEquals("off-the-rails", askedGuard)
    }

    @Then("the client was made to send validators {string}")
    fun validators(validators: String) = assertEquals(validators, askedValidators)

    @Then("the client was made with the reasoner {string}")
    fun reasoner(name: String) = assertEquals(name, askedReasoner.orEmpty())

    @Then("artie's last line is: {}")
    fun lastLine(line: String) = assertEquals(line, printed.lastOrNull(), printed.joinToString("\n"))

    @Then("the trace {string} is YAML: one document for the run, then one for each step")
    fun traceIsYaml(name: String) {
        val documents = yamlDocuments(Files.readString(folder.resolve(name)))
        assertEquals(true, (documents.firstOrNull() as? Map<*, *>)?.containsKey("run"), "the first document: run")
        assertEquals(printed.count { Regex("^\\d+ ").containsMatchIn(it) }, documents.size - 1, "one per step")
    }

    @Then("the trace {string} has these steps")
    fun traceFile(name: String, table: DataTable) {
        val steps = yamlDocuments(Files.readString(folder.resolve(name))).drop(1).map { it as Map<*, *> }
        assertEquals(
            table.asMaps().map { it.getValue("say") to it.getValue("outcome") },
            steps.map { it["say"].toString() to it["outcome"].toString() },
        )
    }

    /** Every document of a YAML stream, as plain maps and lists. */
    private fun yamlDocuments(text: String): List<Any?> = org.yaml.snakeyaml.Yaml().loadAll(text).toList()

    @Then("the line-by-line report {string} shows the script {string} first, then these steps")
    fun lineByLine(name: String, script: String, table: DataTable) {
        val page = Files.readString(folder.resolve(name))
        val text = Files.readString(folder.resolve(script))
        val pre = Regex("""id="script-text">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
        val shown = pre.find(page)?.groupValues?.get(1)
            ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
        // The builder ends the text with a line break, as every line in the page; nothing else may differ.
        assertEquals(text.trimEnd('\n'), shown?.trimEnd('\n'), "the script the page shows first")
        assertTrue(page.indexOf("id=\"script-text\"") < page.indexOf("id=\"run-trace\""), "the script comes first")
        val embedded = Regex("""id="run-trace">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
        val trace = embedded.find(page)?.groupValues?.get(1)
            ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
        val steps = yamlDocuments(checkNotNull(trace) { "no YAML trace in the page" }).drop(1).map { it as Map<*, *> }
        assertEquals(
            table.asMaps().map { it.getValue("say") to it.getValue("outcome") },
            steps.map { it["say"].toString() to it["outcome"].toString() },
        )
    }

    @Then("the run report {string} holds the script {string} and these steps")
    fun report(name: String, script: String, table: DataTable) {
        val page = Files.readString(folder.resolve(name))
        val shown = Regex("""<summary>The script: <code>([^<]*)</code></summary>""").find(page)?.groupValues?.get(1)
        assertEquals(script, shown, "the script the report shows")
        // The trace, as YAML, in the page as text (HTML-escaped), never as JSON.
        val embedded = Regex("""id="run-trace">(.*?)</pre>""", RegexOption.DOT_MATCHES_ALL)
        val trace = embedded.find(page)?.groupValues?.get(1)
            ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")
        val steps = yamlDocuments(checkNotNull(trace) { "no YAML trace in the report" }).drop(1).map { it as Map<*, *> }
        assertEquals(
            table.asMaps().map { it.getValue("say") to it.getValue("outcome") },
            steps.map { it["say"].toString() to it["outcome"].toString() },
        )
    }

    private companion object {
        /** The one copy of heart-run-report's code, as the installed command finds it beside itself. */
        val BUILDER: java.nio.file.Path =
            java.nio.file.Path.of("../../docs/reports/assets/build-run-report.sh").toAbsolutePath().normalize()
    }
}
