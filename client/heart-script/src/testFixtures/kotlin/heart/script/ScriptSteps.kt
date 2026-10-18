package heart.script

import heart.script.host.ScriptHost
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import javax.script.ScriptException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Steps for the script DSL's features: a scripted service, and real scripts run against it. An add-on's
 * features reuse them, adding its package to [imports] and reading the [run] and where it [stopped].
 */
class ScriptSteps {
    val service = ScriptedService()
    private var script = ""

    /** The packages every script imports, as a script host would. */
    val imports = mutableListOf("heart.script.*")
    var run = Run()
        private set
    var stopped: ScriptStopped? = null
        private set
    private val answers = ArrayDeque<Pair<String, String>>()
    private val askedOfPerson = mutableListOf<String>()

    @Given("a service holds these resources")
    fun resources(table: DataTable) = table.asMaps().forEach { row ->
        service.resources[row.getValue("address")] =
            mapOf("title" to row.getValue("title"), "state" to (row["state"] ?: ""))
    }

    @Given("these forms")
    fun forms(table: DataTable) = table.asMaps().forEach {
        service.forms +=
            Form(it.getValue("at"), it.getValue("relation"), it.getValue("leads to"), it.getValue("outcome"))
    }

    @Given("these links")
    fun links(table: DataTable) = table.asMaps().forEach {
        service.links +=
            Link(it.getValue("at"), it.getValue("relation"), it["title"]?.ifEmpty { null }, it.getValue("to"))
    }

    @Given("the server will refuse {string} at {string} as stale: {string}")
    fun refuseStale(rel: String, at: String, detail: String) {
        service.refusals["$rel at $at"] = Answer.Refused(Refusal.Stale, Side.SERVER, detail)
    }

    @Given("{string} becomes {string} after {int} more reads")
    fun settles(address: String, state: String, reads: Int) {
        service.settles[address] = reads to state
    }

    @Given("the script is")
    fun theScript(text: String) {
        script = text
    }

    @When("this script runs")
    fun runs(text: String) {
        script = text
        execute(Run(navigator = { service }, pause = {}))
    }

    @When("the script runs unattended, given")
    fun unattended(table: DataTable) = execute(Run(inputs(table), navigator = { service }, pause = {}))

    @When("the script runs interactively, given")
    fun interactively(table: DataTable) {
        run = Run(inputs(table), interactive = true, prompt = ::answer, navigator = { service }, pause = {})
    }

    @When("the person answers, in order")
    fun personAnswers(table: DataTable) {
        table.asMaps().forEach { answers.addLast(it.getValue("asked") to it.getValue("answer")) }
        execute(run)
    }

    @When("the script's inputs are listed")
    fun listed() = execute(Run(listing = true, navigator = { service }, pause = {}))

    @Then("the script finished")
    fun finished() = assertEquals(null, stopped?.message)

    @Then("the script stopped at step {int}, {string}: {}")
    fun stoppedAt(step: Int, what: String, reason: String) {
        val stop = checkNotNull(stopped) { "the script finished" }
        assertEquals("$step \"$what\" $reason", "${stop.step} ${stop.what} ${stop.reason}")
    }

    @Then("the script stopped before its first request: {}")
    fun stoppedBefore(reason: String) {
        val stop = checkNotNull(stopped) { "the script finished" }
        assertEquals("0 \"${reason.removeSurrounding("\"")}\"", "${stop.step} \"${stop.reason}\"")
    }

    @Then("the client was asked, in order")
    fun askedInOrder(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("call") to it.getValue("what") },
        service.asked,
    )

    @Then("the client was asked nothing")
    fun askedNothing() = assertEquals(emptyList(), service.asked)

    @Then("the script noted {string}")
    fun noted(note: String) = assertTrue(note in run.notes, "notes: ${run.notes}")

    @Then("the script noted, in order")
    fun notedInOrder(table: DataTable) = assertEquals(table.asMaps().map { it.getValue("note") }, run.notes)

    @Then("the run says these defaults were used")
    fun defaults(table: DataTable) = assertEquals(
        table.asMaps().map { it.getValue("question") to it.getValue("default") },
        run.defaultsUsed,
    )

    @Then("they are")
    fun inputsAre(table: DataTable) = assertEquals(
        table.asMaps().map { listOf(it["input"], it["kind"], it["default"].orEmpty(), it["question"].orEmpty()) },
        run.declared.map { listOf(it.name, it.kind, it.default.orEmpty(), it.question.orEmpty()) },
    )

    @Given("the reasoner chooses")
    fun reasonerChooses(table: DataTable) = table.asMaps().forEach { row ->
        val fields = kotlinx.serialization.json.Json.parseToJsonElement(row.getValue("fields"))
            as kotlinx.serialization.json.JsonObject
        service.reasons[row.getValue("intent")] = Choice(
            row.getValue("relation"),
            fields.mapValues { (_, v) -> (v as kotlinx.serialization.json.JsonPrimitive).content },
            follow = row.getValue("selection") == "follow",
        )
    }

    @When("this script runs, allowed a reasoner")
    fun runsReasoning(text: String) {
        script = text
        execute(Run(navigator = { service }, pause = {}).also { it.reasoning = true })
    }

    @Then("trace step {int} is an intent, whose values are")
    fun intentValues(step: Int, table: DataTable) {
        assertEquals("intent", traced(step).asked.mode)
        traceValues(step, table)
    }

    @When("this script runs, written to a trace")
    fun runsTraced(text: String) = runs(text)

    @Then("the trace has these steps")
    fun traceSteps(table: DataTable) = assertEquals(
        table.asMaps().map { listOf(it["step"], it["say"], it["outcome"], it["at"]) },
        run.trace.mapIndexed { i, s -> listOf("${i + 1}", s.asked.say, s.outcome, s.address) },
    )

    @Then("trace step {int}'s values are")
    fun traceValues(step: Int, table: DataTable) = assertEquals(
        table.asMaps().associate { it.getValue("name") to it.getValue("value") },
        traced(step).asked.params.mapValues { (_, v) -> v.toString() },
    )

    @Then("trace step {int} holds {int} data fields, {int} forms and {int} links")
    fun traceHolds(step: Int, fields: Int, forms: Int, links: Int) {
        val now = checkNotNull(traced(step).now) { "trace step $step holds nothing" }
        assertEquals(listOf(fields, forms, links), listOf(now.state.size, now.offered.size, now.links.size))
    }

    @Then("every trace step has when it began and how long it took")
    fun traceTimes() {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(run.traceJson())
        val steps = (json as kotlinx.serialization.json.JsonObject).getValue("steps")
            as kotlinx.serialization.json.JsonArray
        assertEquals(run.trace.size, steps.size)
        steps.forEach { step ->
            val fields = step as kotlinx.serialization.json.JsonObject
            assertTrue(java.time.Instant.parse(fields.getValue("at").toString().trim('"')) <= java.time.Instant.now())
            assertTrue(fields.getValue("ms").toString().toLong() >= 0, "ms: ${fields["ms"]}")
        }
    }

    @Then("trace step {int} was refused as {string}, by {string}: {string}")
    fun traceRefused(step: Int, kind: String, by: String, detail: String) {
        val refused = checkNotNull(traced(step).refused) { "trace step $step was not refused" }
        assertEquals(listOf(kind, by, detail), listOf(refused.kind.words, refused.by.words, refused.detail))
    }

    private fun traced(step: Int): TraceStep = run.trace[step - 1]

    private fun answer(question: String, default: String?): String {
        askedOfPerson += question
        val (asked, answer) = answers.removeFirst()
        assertEquals(asked, question, "the person was asked something else")
        return answer.ifEmpty { default.orEmpty() }
    }

    private fun inputs(table: DataTable): Map<String, String> =
        table.asMaps().associate { it.getValue("input") to it.getValue("value") }

    /** Runs the script as a script: Kotlin's script engine, the DSL imported, the run current. */
    private fun execute(with: Run) {
        run = with
        Run.current = with
        val engine = ScriptHost.engine()
        try {
            engine.eval(imports.joinToString("") { "import $it\n" } + script)
        } catch (e: ScriptException) {
            stopped = generateSequence(e as Throwable) { it.cause }.filterIsInstance<ScriptStopped>().firstOrNull()
                ?: throw e
        } catch (e: ScriptStopped) {
            stopped = e
        }
    }
}
