package steps

import heart.ArtieNavigator
import heart.script.Navigator
import heart.script.NavigatorProvider
import heart.script.Run
import heart.script.ScriptStopped
import heart.script.host.ScriptHost
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import javax.script.ScriptException
import kotlin.test.assertEquals

/** script-github.feature: heart-script scripts, run with ARTIE as their client. */
class ScriptSteps(private val ctx: ClientContext, private val gateway: GitHubGatewaySteps) {
    private var stopped: ScriptStopped? = null

    /** How the last run stopped, in its own words; null when it finished. */
    fun stoppedMessage(): String? = stopped?.message

    private var reasoner: heart.Reasoner? = null

    @io.cucumber.java.en.Given("ARTIE's reasoner will choose to act on {string} with {}")
    fun reasonerChooses(form: String, fields: String) {
        val chosen = kotlinx.serialization.json.Json.parseToJsonElement(fields) as kotlinx.serialization.json.JsonObject
        reasoner = ScriptedReasoner(heart.Selection.Act(form, chosen))
    }

    @io.cucumber.java.en.Given("ARTIE's reasoner is Claude, through the scripted Claude API")
    fun reasonerIsClaude() {
        reasoner = heart.ClaudeReasoner(ctx.transport, "test-key")
    }

    @When("this script runs with ARTIE, allowed its reasoner")
    fun runsReasoning(script: String) =
        runs(script, ArtieNavigator(ctx.client, reasoner, exchangeLog = ctx.exchangeLog, guard = guard()))

    @When("this script runs with ARTIE")
    fun runs(script: String) =
        runs(script, ArtieNavigator(ctx.client, exchangeLog = ctx.exchangeLog, guard = guard()))

    /** The scenario's lock file and how it is held to (script-guard.feature); null when the run is not guarded. */
    private fun guard(): heart.ScriptGuard? = ctx.pins?.let { pins ->
        val forms = pins.mapValues { (_, pin) -> heart.script.FormPin(pin.first, listOfNotNull(pin.second)) }
        heart.ScriptGuard(heart.script.Pins(forms = forms), strict = ctx.guard == "strict")
    }

    private fun runs(script: String, navigator: ArtieNavigator) {
        Run.current = Run(navigator = { navigator }).also { it.reasoning = true; it.source = script }
        val engine = ScriptHost.engine(listOf("heart.script.*"))
        try {
            engine.eval(script)
        } catch (e: ScriptException) {
            stopped = generateSequence(e as Throwable) { it.cause }.filterIsInstance<ScriptStopped>().firstOrNull()
                ?: throw e
        } catch (e: ScriptStopped) {
            stopped = e
        }
    }

    /** The network fails for [path] as Java's HTTP client does with nothing listening: no message. */
    @io.cucumber.java.en.Given("the gateway can't be reached for {string}")
    fun unreachable(path: String) {
        val gateway = checkNotNull(ctx.inProcess) { "no gateway yet" }
        ctx.inProcess = object : heart.Transport {
            override fun send(request: heart.HeartRequest): heart.HeartResponse =
                if (java.net.URI(request.url).path == path) throw java.net.ConnectException() else gateway.send(request)
        }
    }

    private var found: Navigator? = null

    @When("a script host looks for a HEART client")
    fun looks() {
        found = NavigatorProvider.load()
    }

    @Then("it finds ARTIE")
    fun findsArtie() = assertEquals(ArtieNavigator::class, found?.let { it::class })

    @Then("the script stopped before its first request: {}")
    fun stoppedBefore(reason: String) {
        val stop = checkNotNull(stopped) { "the script finished" }
        assertEquals("0 $reason", "${stop.step} ${stop.reason}")
    }

    @Then("the script finished")
    fun finished() = assertEquals(null, stopped?.message)

    @Then("the script stopped at step {int}, {string}: {}")
    fun stoppedAt(step: Int, what: String, reason: String) {
        val stop = checkNotNull(stopped) { "the script finished" }
        assertEquals("$step \"$what\" $reason", "${stop.step} ${stop.what} ${stop.reason}")
    }

    @Then("issue {long} of {string} is now {string} on the small GitHub")
    fun issueIs(number: Long, repository: String, state: String) {
        val issue = gateway.github.issues.first { "${it.owner}/${it.repo}" == repository && it.number == number }
        assertEquals(state, issue.state)
    }

    @Then("issue {long} of {string} now has these comments on the small GitHub")
    fun comments(number: Long, repository: String, table: DataTable) {
        val (owner, repo) = repository.split("/")
        val held = gateway.github.comments.filter { it.owner == owner && it.repo == repo && it.issue == number }
        val expected = table.asMaps().map { it.getValue("author") to it.getValue("body") }
        assertEquals(expected, held.map { it.author to it.body })
    }
}
