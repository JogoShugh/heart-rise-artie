package steps

import heart.ArtieNavigator
import heart.Selection
import heart.cli.BadArguments
import heart.cli.goalFile
import heart.script.Move
import heart.script.PlanView
import heart.script.Planner
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** goal-run.feature: a goal file, done move by move by a scripted planner, with ARTIE's scripted reasoner. */
class GoalSteps(
    private val ctx: ClientContext,
    private val scripts: ScriptSteps,
    private val replay: ReplaySteps,
) {
    private var moves: List<Move> = emptyList()
    private var choices: List<Selection> = emptyList()
    private val shown = mutableListOf<PlanView>()
    private var refused: String? = null

    /** What the planner's model answers when it can't, in the API's words; null when it can. */
    private var plannerFails: String? = null

    @Given("the planner will move")
    fun plannerMoves(table: DataTable) {
        moves = table.asMaps().map(::move)
    }

    @Given("the planner's model will fail: {string}")
    fun plannerFails(said: String) {
        plannerFails = said
    }

    /** The reasoner of the last goal run, to count what it was asked. */
    internal var reasoner: ScriptedReasoner? = null

    @Then("the planner was shown the goal with {string}")
    fun goalShown(words: String) {
        val goal = shown.last().goal
        assertTrue(words in goal, goal)
    }

    @Then("at move {int} the planner was told the step now is {string}")
    fun toldNowAt(move: Int, current: String) = assertEquals(current, shown[move - 1].current)

    @Then("the planner was last told the step now is {string}")
    fun toldNow(current: String) = assertEquals(current, shown.last().current)

    @Then("the run's capture keeps each check as decided, with where it was decided")
    fun checksKept(table: DataTable) {
        val planned = checkNotNull(scripts.lastRun).planned
        table.asMaps().map { it.getValue("move") }.forEach { move ->
            assertTrue(move in planned, "no \"$move\" in:\n${planned.joinToString("\n")}")
        }
    }

    @Then("at move {int} the planner was told the step now includes {string}")
    fun toldNowIncludesAt(move: Int, words: String) {
        val current = shown[move - 1].current
        assertTrue(words in current, current)
    }

    @Then("at move {int} the planner was shown the resource's {string} as {string}")
    fun shownMember(move: Int, member: String, value: String) {
        val now = shown[move - 1].held.state[member]
        assertEquals(value, (now as? kotlinx.serialization.json.JsonPrimitive)?.content ?: now.toString())
    }

    @Then("ARTIE's reasoner was last asked to {string}")
    fun reasonerLastAsked(intent: String) = assertEquals(intent, checkNotNull(reasoner).intents.last())

    @Then("the planner was asked {int} times")
    fun plannerAsked(times: Int) = assertEquals(times, shown.size)

    @Then("ARTIE's reasoner was asked {int} times")
    fun reasonerAsked(times: Int) = assertEquals(times, checkNotNull(reasoner).shown.size)

    @Given("ARTIE's reasoner will choose, in order")
    fun reasonerChooses(table: DataTable) {
        choices = table.asMaps().map { row ->
            // A row with a link to follow, in place of a form.
            row["follow"]?.takeIf { it.isNotBlank() }?.let { Selection.Follow(it, null) }
                ?: Selection.Act(row.getValue("form"), Json.parseToJsonElement(row.getValue("fields")) as JsonObject)
        }
    }

    @When("the goal {string} runs with ARTIE, captured in {string}, given")
    fun goalRuns(file: String, capture: String, table: DataTable) {
        refused = null
        val given = table.asMaps().associate { it.getValue("name") to it.getValue("value") }
        val text = Files.readString(replay.folder.resolve(file)).replace("\\n", "\n")
        val goal = try {
            goalFile(file, text)
        } catch (e: BadArguments) {
            refused = e.message
            return
        }
        reasoner = ScriptedReasoner(*choices.toTypedArray())
        // As a host wires it (goal-rise.feature): reading what a link leads to as it is, on the scenario's wire.
        val navigator = ArtieNavigator(ctx.client, reasoner)
            .let { if (ctx.wired) heart.WiredNavigator(it, ctx.bare) else it }
        // Each listener of a watch with listeners (Whenevers side by side) asks for a client of its own.
        val run = scripts.runGoal(goal, planner(), navigator, given) {
            ArtieNavigator(ctx.newClient(ctx.dialect, ctx.rung), reasoner)
        }
        Files.writeString(replay.folder.resolve(capture), run.traceYaml())
    }

    @Then("the planner was shown these sentences")
    fun sentencesShown(table: DataTable) =
        assertEquals(table.asMaps().map { it.getValue("sentence") }, shown.first().sentences)

    @Then("the planner was told {string}")
    fun told(words: String) {
        val said = shown.last().moves
        assertTrue(said.any { it.startsWith(words) }, said.joinToString("\n"))
    }

    @Then("the planner was last shown {string} described as {string}")
    fun describedAs(form: String, words: String) {
        val described = shown.last().described[form].orEmpty()
        assertTrue(words in described, "$form: \"$described\"")
    }

    @Then("the goal run stopped, saying {string}")
    fun stoppedSaying(said: String) {
        val message = refused ?: checkNotNull(scripts.stoppedMessage()) { "the goal run finished" }
        assertTrue(said in message, message)
    }

    @Then("the script {string} nests its acts as")
    fun nests(script: String, table: DataTable) {
        val acts = Files.readAllLines(replay.folder.resolve(script)).mapNotNull { line ->
            ACT.find(line)?.let { mapOf("relation" to it.groupValues[2], "depth" to "${it.groupValues[1].length / 4}") }
        }
        assertEquals(table.asMaps(), acts)
    }

    @Then("the script {string} has a line with {string}")
    fun hasLine(script: String, text: String) {
        val lines = Files.readAllLines(replay.folder.resolve(script))
        assertTrue(lines.any { text in it }, lines.joinToString("\n"))
    }

    @Then("the script {string} has no line with {string}")
    fun hasNoLine(script: String, text: String) {
        val lines = Files.readAllLines(replay.folder.resolve(script))
        assertTrue(lines.none { text in it }, lines.joinToString("\n"))
    }

    /** Line [number] of [name] put as [text], [indent] spaces before it, each `\n` in it a line break. */
    @Given("line {int} of {string} reads {string}, indented {int}")
    fun lineReads(number: Int, name: String, text: String, indent: Int) {
        val file = replay.folder.resolve(name)
        val lines = Files.readAllLines(file)
        lines[number - 1] = " ".repeat(indent) + text.replace("\\n", "\n")
        Files.writeString(file, lines.joinToString("\n") + "\n")
    }

    @Then("at move {int} the planner was shown the steps")
    fun stepsAt(move: Int, table: DataTable) =
        assertEquals(table.asMaps().map { it.getValue("line") }, shown[move - 1].steps.map { it.trim() })

    private var entered: heart.script.Representation? = null

    @When("ARTIE's navigator enters {string}")
    fun navigatorEnters(url: String) {
        entered = (ArtieNavigator(ctx.client).enter(url) as heart.script.Answer.Done).now
    }

    @Then("the models are shown these links")
    fun modelsShown(table: DataTable) {
        val shown = checkNotNull(entered).followable.flatMap { (rel, titles) ->
            titles.map { mapOf("relation" to rel, "title" to it.orEmpty()) }
        }
        assertEquals(table.asMaps(), shown)
    }

    @Then("the run keeps these relations: {}")
    fun keeps(relations: String) =
        assertEquals(relations.split(",").map { it.trim() }, checkNotNull(entered).links.keys.toList())

    @Then("the capture {string} keeps the planner's moves, the first")
    fun keptMoves(capture: String, table: DataTable) {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        val kept = ((head["run"] as Map<*, *>)["moves"] as? List<*>).orEmpty().map { it.toString() }
        val expected = table.asMaps().map { it.getValue("move") }
        assertEquals(expected, kept.take(expected.size), kept.joinToString("\n"))
    }

    @Then("the capture {string} keeps the watch as this script, from step {int}")
    fun keptWatch(capture: String, from: Int, script: String) {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        val watch = (head["run"] as Map<*, *>)["watch"] as? Map<*, *>
        assertEquals(script.trimEnd(), (watch?.get("script") as? String)?.trimEnd(), "watch: $watch")
        assertEquals(from, watch?.get("from"))
    }

    @Then("the capture {string} keeps a watch script with a line with {string}")
    fun watchLine(capture: String, text: String) {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        val script = ((head["run"] as Map<*, *>)["watch"] as? Map<*, *>)?.get("script")?.toString().orEmpty()
        assertTrue(script.lines().any { text in it }, script)
    }

    @Then("the capture {string} keeps the watch as data")
    fun watchData(capture: String, steps: String) {
        val kept = watched(capture)?.get("steps")
        assertEquals(Yaml().load<Any?>(steps), kept, Yaml().dump(kept))
    }

    @Then("the capture {string} keeps the watch as data, its Once's otherwise")
    fun watchOtherwise(capture: String, steps: String) {
        val kept = watched(capture)?.get("steps")
        assertEquals(Yaml().load<Any?>(steps), onceIn(kept)?.get("otherwise"), Yaml().dump(kept))
    }

    @Then("the capture {string} keeps, in the watch as data, a step with")
    fun watchStepWith(capture: String, step: String) {
        val watch = watched(capture)
        val expected = Yaml().load<Map<*, *>>(step)
        assertTrue(
            holds(watch?.get("steps"), expected),
            "no step with $expected in:\n${Yaml().dump(watch?.get("steps"))}\nThe script:\n${watch?.get("script")}",
        )
    }

    /** Whether [data], or anything in it however deep, is a map holding [expected]: its keys, each with its value. */
    private fun holds(data: Any?, expected: Map<*, *>): Boolean = when (data) {
        is Map<*, *> -> covers(data, expected) || data.values.any { holds(it, expected) }
        is List<*> -> data.any { holds(it, expected) }
        else -> false
    }

    /** Whether [actual] has each key of [expected], each value covering its own: a map by key, a list by item. */
    private fun covers(actual: Any?, expected: Any?): Boolean = when (expected) {
        is Map<*, *> -> actual is Map<*, *> &&
            expected.all { (key, value) -> key in actual && covers(actual[key], value) }
        is List<*> -> actual is List<*> && actual.size == expected.size &&
            expected.indices.all { covers(actual[it], expected[it]) }
        else -> actual == expected
    }

    /** The capture's watch: its first lines' run, then its watch. */
    private fun watched(capture: String): Map<*, *>? {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        return (head["run"] as Map<*, *>)["watch"] as? Map<*, *>
    }

    /** The first Once anywhere in [data], a watch's steps as data. */
    private fun onceIn(data: Any?): Map<*, *>? = when (data) {
        is Map<*, *> -> data["once"] as? Map<*, *> ?: data.values.firstNotNullOfOrNull { onceIn(it) }
        is List<*> -> data.firstNotNullOfOrNull { onceIn(it) }
        else -> null
    }

    @Then("the capture {string} keeps a watch script with no line with {string}")
    fun noWatchLine(capture: String, text: String) {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        val script = ((head["run"] as Map<*, *>)["watch"] as? Map<*, *>)?.get("script")?.toString().orEmpty()
        assertTrue(script.isNotEmpty() && script.lines().none { text in it }, script)
    }

    @Then("the capture {string} has no step whose words hold {string}")
    fun noStepSays(capture: String, words: String) {
        val said = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).drop(1)
            .map { (it as Map<*, *>)["say"].toString() }
        assertTrue(said.none { words in it }, said.filter { words in it }.joinToString("\n"))
    }

    @Then("the planner was shown these steps, move by move")
    fun stepsShown(table: DataTable) {
        val rows = table.asMaps()
        assertEquals(rows.map { it.getValue("step") }, shown.map { it.current })
        rows.zip(shown).forEach { (row, view) ->
            assertTrue(row.getValue("at") in view.held.address, "${row["step"]}: at ${view.held.address}")
        }
    }

    @Then("the planner was last shown {string} named, at an address ending {string}")
    fun named(name: String, ending: String) {
        val address = shown.last().named[name]
        assertTrue(address?.endsWith(ending) == true, "$name: $address")
    }

    @Then("the script {string} has no escaped quote")
    fun noEscapedQuote(script: String) {
        val lines = Files.readAllLines(replay.folder.resolve(script))
        assertTrue(lines.none { "\\\"" in it }, lines.joinToString("\n"))
    }

    @Then("the run's script kept in {string} has a line with {string}")
    fun keptHasLine(capture: String, text: String) {
        val lines = kept(capture)
        assertTrue(lines.any { text in it }, lines.joinToString("\n"))
    }

    @Then("the run's script kept in {string} has no escaped quote")
    fun keptNoEscapedQuote(capture: String) {
        val lines = kept(capture)
        assertTrue(lines.none { "\\\"" in it }, lines.joinToString("\n"))
    }

    /** The lines of the script a goal run wrote, one a move, as its capture keeps it under `run: source:`. */
    private fun kept(capture: String): List<String> {
        val head = Yaml().loadAll(Files.readString(replay.folder.resolve(capture))).first() as Map<*, *>
        return ((head["run"] as Map<*, *>)["source"] as String).lines()
    }

    /** The scripted moves, one a call; once they run out, a "loop" planner makes a move that does nothing. */
    private fun planner(): Planner {
        val left = ArrayDeque(moves)
        return Planner { view ->
            shown += view
            plannerFails?.let { throw heart.script.ModelFailed(it) }
            if (left.size == 1 && left.first() === LOOP) LOOP else left.removeFirstOrNull() ?: Move.Finish
        }
    }

    private fun move(row: Map<String, String>): Move {
        val sentence = row["sentence"]?.takeIf { it.isNotBlank() }?.toInt()
        return when (val kind = row.getValue("move")) {
            "act" -> Move.Act(
                row.getValue("words"), sentence, row["opens"] == "yes",
                asOf = row["as of"]?.takeIf { it.isNotBlank() }, refusedAs = row["refused"]?.takeIf { it.isNotBlank() },
            )
            // A goal in steps' done is for the step now, whatever sentence it names.
            "done" -> Move.Done(sentence ?: 0)
            "back" -> Move.Back(row["to"] ?: row.getValue("words"))
            "stop" -> Move.Stop(row.getValue("words"))
            "wait" -> Move.Wait(row["relation"].orEmpty(), tries = 3)
            "check" -> Move.Check(facts(row.getValue("facts")))
            "clarify" -> Move.Clarify(row.getValue("question"))
            "finish" -> Move.Finish
            "loop" -> LOOP
            else -> error("no move $kind")
        }
    }

    private fun facts(json: String): Map<String, Any?> = (Json.parseToJsonElement(json) as JsonObject)
        .mapValues { (_, value) ->
            // A list of values: in a match, every item must be among the member's.
            if (value is kotlinx.serialization.json.JsonArray) return@mapValues value.map { it.jsonPrimitive.content }
            val primitive = value.jsonPrimitive
            if (primitive.isString) primitive.content else primitive.booleanOrNull ?: primitive.longOrNull
                ?: primitive.doubleOrNull
        }

    private companion object {
        /** A move that does nothing, made again and again: a planner in a loop, waiting for no form. */
        val LOOP = Move.Wait("")

        val ACT = Regex("""^( *)act\("([^"]+)"""")
    }
}
