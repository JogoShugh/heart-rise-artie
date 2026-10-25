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
    fun runs(script: String) {
        // Each listener of a watch with listeners asks for a client of its own: a new one, as production makes it.
        val made = { client: heart.HeartRiseClient ->
            ArtieNavigator(client, exchangeLog = ctx.exchangeLog, guard = guard(), guarding = ctx.guard)
        }
        navigators = { heart.WiredNavigator(made(ctx.newClient(ctx.dialect, ctx.rung)), ctx.bare) }
        val began = System.nanoTime()
        try {
            runs(script, made(ctx.client))
        } finally {
            navigators = null
            took = java.time.Duration.ofNanos(System.nanoTime() - began)
        }
    }

    /** How long the last script run took, on the real clock. */
    private var took: java.time.Duration = java.time.Duration.ZERO

    @Then("the run took less than {int} seconds")
    fun tookLessThan(seconds: Long) =
        kotlin.test.assertTrue(took < java.time.Duration.ofSeconds(seconds), "the run took $took")

    /** Where the run gets each client after its first, when a script asks for more than one; else the one. */
    private var navigators: (() -> Navigator)? = null

    /** The scenario's lock file and how it is held to (script-guard.feature); null when the run is not guarded. */
    private fun guard(): heart.ScriptGuard? = ctx.pins?.let { pins ->
        val forms = pins.mapValues { (_, pin) -> heart.script.FormPin(pin.first, listOfNotNull(pin.second)) }
        heart.ScriptGuard(heart.script.Pins(forms = forms, links = ctx.linkPins), strict = ctx.guard == "strict")
    }

    private fun runs(script: String, navigator: ArtieNavigator) {
        // As the provider makes it: a bare script's calls go out on the scenario's bare wire.
        run(script, heart.WiredNavigator(navigator, ctx.bare), emptyMap())
    }

    /** [goal] done move by move as [planner] chooses, with [navigator], given [inputs] (goal-run.feature). */
    fun runGoal(
        goal: heart.script.Goal,
        planner: heart.script.Planner,
        navigator: ArtieNavigator,
        inputs: Map<String, String>,
        more: (() -> Navigator)? = null,
    ): Run {
        stopped = null
        navigators = more
        val run = fresh(inputs, navigator)
        lastRun = run
        Run.current = run
        try {
            heart.script.runGoal(goal, planner)
        } catch (e: ScriptStopped) {
            stopped = e
        } finally {
            navigators = null
        }
        return run
    }

    /** The last script's run, for what it noted (script-watch.feature). */
    var lastRun: Run? = null

    /** Whether the run's pauses are instant, its clock moved by them alone (script-watch.feature). */
    var clocked = false

    /** The run's clock, when [clocked]: the time its pauses add up to. */
    var now: java.time.Instant = java.time.Instant.parse("2026-10-23T12:00:00Z")

    /** The last run's virtual time, when [clocked]. */
    private var lastTime: heart.script.VirtualTime? = null

    /** A run with [navigator], given [inputs]; when [clocked], its pauses are instant and move its clock. */
    private fun fresh(inputs: Map<String, String>, navigator: Navigator): Run {
        var first = true
        val more = navigators
        val navigators = { if (first || more == null) navigator.also { first = false } else more() }
        return if (clocked) {
            // Virtual time: it moves only once every thread of a watch waits (Time.kt). A run after another
            // begins where the one before ended.
            now = lastTime?.now() ?: now
            val time = heart.script.VirtualTime(now).also { lastTime = it }
            Run(inputs, navigator = navigators, pause = time::pause).also {
                it.clock = time::now
                it.time = time
            }
        } else {
            Run(inputs, navigator = navigators)
        }.also { it.remember = remember }
    }

    /** Where the scenario's runs remember what their watches did; null when they don't. */
    private var remember: java.nio.file.Path? = null
    private var script: String? = null

    @io.cucumber.java.en.Given("the run remembers in {string}")
    fun remembers(name: String) {
        remember = java.nio.file.Files.createTempDirectory("watch").resolve(name)
    }

    @io.cucumber.java.en.Given("the script is")
    fun scriptIs(text: String) {
        script = text
    }

    @When("the script runs with ARTIE")
    fun scriptRuns() = runs(checkNotNull(script))

    @When("the script runs with ARTIE again")
    fun scriptRunsAgain() = runs(checkNotNull(script))

    @io.cucumber.java.en.Given("the run's clock moves only when the run pauses")
    fun clockMovesWithPauses() {
        clocked = true
    }

    /** [script] run with [navigator], given [inputs]; the run, for its capture (script-replay.feature). */
    fun run(script: String, navigator: Navigator, inputs: Map<String, String>): Run {
        stopped = null
        val run = fresh(inputs, navigator).also { it.reasoning = true; it.source = script }
        lastRun = run
        Run.current = run
        val engine = ScriptHost.engine(listOf("heart.script.*"))
        try {
            engine.eval(script)
        } catch (e: ScriptException) {
            stopped = generateSequence(e as Throwable) { it.cause }.filterIsInstance<ScriptStopped>().firstOrNull()
                ?: throw e
        } catch (e: ScriptStopped) {
            stopped = e
        }
        return run
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
    fun findsArtie() =
        assertEquals(ArtieNavigator::class, (found as? heart.WiredNavigator)?.navigator?.let { it::class })

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
        // A blank row is a comment not expected, in an example where there is none.
        val expected = table.asMaps().filter { !it["author"].isNullOrBlank() || !it["body"].isNullOrBlank() }
            .map { it.getValue("author") to it.getValue("body") }
        assertEquals(expected, held.map { it.author to it.body })
    }
}
