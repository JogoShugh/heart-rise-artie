package steps

import com.artie.heart.gateway.fixtures.GitHubGateway
import heart.ArtieNavigator
import heart.ScriptGuard
import heart.cli.arguments
import heart.cli.config
import heart.cli.generate.generate
import heart.cli.lockFile
import heart.cli.repin
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * script-replay.feature: a script run with ARTIE against the GitHub gateway, artie generate's script and lock file
 * made from its capture, and that script run in turn, in a folder of the scenario's own.
 */
class ReplaySteps(
    private val ctx: ClientContext,
    private val scripts: ScriptSteps,
    private val gateway: GitHubGatewaySteps,
) {
    private val folder: Path = Files.createTempDirectory("artie-replay")

    @Given("the script {string} is")
    fun scriptIs(name: String, text: String) {
        Files.writeString(folder.resolve(name), text)
    }

    @Given("{string} ran with ARTIE, captured in {string}, given")
    @When("{string} runs with ARTIE, captured in {string}, given")
    fun runs(script: String, capture: String, table: DataTable) = run(script, capture, table, guard = null)

    @When("{string} runs with ARTIE, held {string} to its lock file, captured in {string}, given")
    fun runsGuarded(script: String, mode: String, capture: String, table: DataTable) =
        run(script, capture, table, guard = mode)

    @When("{string} runs with ARTIE, held {string} to its lock file, with no-repin, captured in {string}, given")
    fun runsGuardedNoRepin(script: String, mode: String, capture: String, table: DataTable) =
        run(script, capture, table, guard = mode, options = listOf("--no-repin"))

    @Given("the file {string} is")
    fun fileIs(name: String, text: String) {
        Files.writeString(folder.resolve(name), text)
    }

    /** What the run said about its lock file after it ran, as artie run says it. */
    private var aboutPins = listOf<String>()

    /** A run as artie run makes it: its arguments read, its config and lock file found beside the script. */
    private fun run(
        script: String,
        capture: String,
        table: DataTable,
        guard: String?,
        options: List<String> = emptyList(),
    ) {
        val file = folder.resolve(script)
        val guarded = guard?.let { listOf("--guard", it) }.orEmpty()
        val asked = arguments(listOf("run", script) + guarded + options)
        val config = config(asked, folder, file)
        val held = guard?.let {
            val lock = lockFile(asked, folder, file, config)
            ScriptGuard(checkNotNull(lock.pins) { "no lock file beside $script" }, strict = lock.guard == "strict")
        }
        val given = table.asMaps().associate { it.getValue("name") to it.getValue("value") }
        val run = scripts.run(Files.readString(file), ArtieNavigator(ctx.client, guard = held), given)
        Files.writeString(folder.resolve(capture), run.traceYaml())
        aboutPins = if (guard == null) emptyList() else repin(asked, folder, file, config, run.traceYaml())
    }

    @Then("about its pins, the run said {string}")
    fun saidAboutPins(said: String) = assertEquals(said, aboutPins.joinToString("; "))

    @Given("artie generated {string} from {string} and {string}")
    fun generated(script: String, capture: String, source: String) {
        val said = ByteArrayOutputStream()
        generate(listOf("generate", capture, "--out", script, "--script", source), folder, PrintStream(said, true))
    }

    @Given("the gateway's issue forms change: {}")
    fun formsChange(change: String) {
        val (was, now) = CHANGES.getValue(change)
        val shipped = Path.of("..", "..", "heart", "heart-gateway", "connectors", "github")
        val connector = Files.createTempDirectory("github-connector")
        Files.list(shipped).use { files -> files.forEach { Files.copy(it, connector.resolve(it.fileName)) } }
        val issue = connector.resolve("issue.resource.yaml")
        val text = Files.readString(issue)
        check(was in text) { "issue.resource.yaml has no \"$was\" to change" }
        Files.writeString(issue, text.replaceFirst(was, now))
        ctx.inProcess = InProcessTransport(GitHubGateway.handler(gateway.github, connector))
    }

    @Then("the lock file {string} pins rung {string}")
    fun lockRung(lock: String, rung: String) =
        assertEquals(rung, Yaml().load<Map<String, Any?>>(Files.readString(folder.resolve(lock)))["rung"])

    @Then("the run captured in {string} made the selections the run captured in {string} made, with these values")
    fun sameSelections(made: String, from: String, table: DataTable) {
        val values = table.asMaps().map { it.getValue("was") to it.getValue("now") }
        val expected = selections(from).map { line ->
            values.fold(line) { text, (was, now) ->
                Regex("(?<![\\w.])${Regex.escape(was)}(?![\\w])").replace(text, now)
            }
        }
        assertEquals(expected, selections(made))
    }

    /** Each selection a capture made, in order: its relation and the values it sent, as text. */
    private fun selections(capture: String): List<String> =
        Yaml().loadAll(Files.readString(folder.resolve(capture))).toList().drop(1)
            .map { it as Map<*, *> }
            .mapNotNull { step -> (step["params"] as? Map<*, *>)?.takeIf { "relation" in it } }
            .map { params -> params.entries.joinToString(", ") { (k, v) -> "$k=$v" } }

    private companion object {
        /** Each change to the issue resource the gateway serves: the connector's text, and what it becomes. */
        val CHANGES = mapOf(
            "nothing" to ("noun: issue" to "noun: issue"),
            "close takes a milestone" to (
                "    body: { state: closed, state_reason: completed }\n" to
                    "    body: { state: closed, state_reason: completed }\n    fields: [milestone]\n"
                ),
            "edit takes no body" to ("fields: [title, body]" to "fields: [title]"),
        )
    }
}
