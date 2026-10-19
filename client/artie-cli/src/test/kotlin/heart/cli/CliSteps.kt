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
            script.service
        }
        val cli = ArtieCli(folder, PrintStream(bytes, true), { typed.removeFirstOrNull() }, client, BUILDER)
        exit = cli.run(args)
        printed = bytes.toString().lines().filter { it.isNotBlank() }
    }

    @Then("artie exits {int}")
    fun exits(code: Int) = assertEquals(code, exit, printed.joinToString("\n"))

    @Then("artie prints")
    fun prints(table: DataTable) = assertEquals(table.asMaps().map { it.getValue("line") }, printed)

    @Then("artie prints, exactly")
    fun printsExactly(lines: String) = assertEquals(lines, printed.joinToString("\n"))

    @Then("the client was made to ask at rung {string}")
    fun rung(rung: String) = assertEquals(rung, askedRung.orEmpty())

    @Then("the client was made to keep its exchanges")
    fun keepsExchanges() = assertEquals(true, askedExchanges)

    @Then("the client was made not to keep its exchanges")
    fun keepsNoExchanges() = assertEquals(false, askedExchanges)

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
