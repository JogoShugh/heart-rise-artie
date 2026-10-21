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

    @Given("the planner will move")
    fun plannerMoves(table: DataTable) {
        moves = table.asMaps().map(::move)
    }

    @Given("ARTIE's reasoner will choose, in order")
    fun reasonerChooses(table: DataTable) {
        choices = table.asMaps().map { row ->
            Selection.Act(row.getValue("form"), Json.parseToJsonElement(row.getValue("fields")) as JsonObject)
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
        val navigator = ArtieNavigator(ctx.client, ScriptedReasoner(*choices.toTypedArray()))
        val run = scripts.runGoal(goal, planner(), navigator, given)
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
            if (left.size == 1 && left.first() === LOOP) LOOP else left.removeFirstOrNull() ?: Move.Finish
        }
    }

    private fun move(row: Map<String, String>): Move {
        val sentence = row["sentence"]?.takeIf { it.isNotBlank() }?.toInt()
        return when (val kind = row.getValue("move")) {
            "act" -> Move.Act(row.getValue("words"), sentence, row["opens"] == "yes")
            "done" -> Move.Done(checkNotNull(sentence))
            "back" -> Move.Back(row.getValue("to"))
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
            val primitive = value.jsonPrimitive
            if (primitive.isString) primitive.content else primitive.booleanOrNull ?: primitive.longOrNull
                ?: primitive.doubleOrNull
        }

    private companion object {
        /** A move that does nothing, made again and again: a planner in a loop. */
        val LOOP = Move.Done(1)

        val ACT = Regex("""^( *)act\("([^"]+)"""")
    }
}
