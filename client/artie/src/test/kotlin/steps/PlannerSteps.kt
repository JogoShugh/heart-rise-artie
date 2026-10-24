package steps

import heart.ClaudePlanner
import heart.ClaudeReasoner
import heart.HeartResponse
import heart.script.Move
import heart.script.PlanView
import heart.script.Representation
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** goal-planner.feature: the Claude planner, against the scripted Claude API. */
class PlannerSteps(private val ctx: ClientContext) {
    private var planner: ClaudePlanner? = null
    private var held = Representation("", JsonObject(emptyMap()), emptySet(), emptyMap())
    private var view: PlanView? = null
    private var move: Move? = null

    @Given("the planner is Claude, through the scripted Claude API")
    fun plannerIsClaude() {
        planner = ClaudePlanner(ctx.transport, "test-key")
    }

    @Given("the planner holds {string} with the state {}, offering")
    fun holds(address: String, state: String, table: DataTable) {
        val forms = table.asMaps().map { it.getValue("form") }.toSet()
        held = Representation(address, Json.parseToJsonElement(state) as JsonObject, forms, emptyMap())
    }

    @Given("the held resource embeds under {string} resources named")
    fun embeds(rel: String, table: DataTable) {
        val embedded = table.asMaps().map { row ->
            val name = row.getValue("name")
            heart.script.Embedded(null, Json.parseToJsonElement("{\"name\": \"$name\"}") as JsonObject, "/labels/$name")
        }
        held = Representation(held.address, held.state, held.offered, held.links, mapOf(rel to embedded))
        view = view?.let {
            PlanView(it.goal, it.sentences, it.done, it.moves, it.path, held, it.answers, it.inputs)
        }
    }

    @Given("the goal is {string} with the input number = {int}, the first sentence done")
    fun goal(text: String, number: Int) {
        val filled = text.replace("\$number", "$number").replace("\$owner/\$repo", "acme/rockets")
        val sentences = heart.script.Goal("", emptyList(), filled).sentences(filled)
        view = PlanView(
            filled, sentences, setOf(1), listOf("open issue 10 of acme/rockets: at ${held.address}"),
            listOf("https://github.gateway.test/", held.address), held, emptyList(), mapOf("number" to "$number"),
        )
    }

    @Given("the Claude API will answer with the move {}")
    fun answersMove(input: String) {
        val body = """{"id":"msg_1","type":"message","role":"assistant","content":[""" +
            """{"type":"tool_use","id":"t1","name":"move","input":$input}],"stop_reason":"tool_use"}"""
        ctx.transport.respond("POST", ClaudeReasoner.ENDPOINT, HeartResponse(OK, emptyMap(), body))
    }

    @Given("the Claude API will answer, using {}, with the move {}")
    fun answersMoveUsing(usage: String, input: String) {
        val body = """{"id":"msg_1","type":"message","role":"assistant","content":[""" +
            """{"type":"tool_use","id":"t1","name":"move","input":$input}],"stop_reason":"tool_use",""" +
            """"usage":$usage}"""
        ctx.transport.respond("POST", ClaudeReasoner.ENDPOINT, HeartResponse(OK, emptyMap(), body))
    }

    @Then("the planner counted")
    fun counted(table: DataTable) = assertEquals(
        table.asMaps().single().mapValues { it.value.toLong() },
        (checkNotNull(planner) as ClaudePlanner).usage.counts(),
    )

    @When("the planner is asked its next move")
    fun asked() {
        move = checkNotNull(planner).next(checkNotNull(view))
    }

    @Then("the move is {}")
    fun moveIs(said: String) = assertEquals(said, words(checkNotNull(move)))

    @Then("the planner's request says")
    fun requestSays(table: DataTable) {
        val text = request().getValue("messages").jsonArray.last().jsonObject.getValue("content").jsonArray
            .first().jsonObject.getValue("text").jsonPrimitive.content
        table.asMaps().map { it.getValue("part") }.forEach { assertTrue(it in text, "no \"$it\" in:\n$text") }
    }

    @Given("the goal is in steps, now at {string}, with {string} named at {string}")
    fun goalInSteps(now: String, name: String, address: String) {
        view = PlanView(
            "Fix the Safari issue.", emptyList(), emptySet(), listOf("done: Open issue 10 of acme/rockets"),
            listOf("https://github.gateway.test/", held.address), held, emptyList(), mapOf("number" to "10"),
            current = now,
            steps = listOf("[done] Open issue 10 of acme/rockets (as issue)", "  [now] $now"),
            allowed = setOf("check", "clarify", "stop"),
            named = mapOf(name to address),
        )
    }

    @Then("the planner's tool offers only the moves")
    fun toolOffers(table: DataTable) {
        val tool = request().getValue("tools").jsonArray.first().jsonObject
        val offered = tool.getValue("input_schema").jsonObject.getValue("properties").jsonObject
            .getValue("move").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(table.asMaps().map { it.getValue("move") }, offered)
    }

    @Then("the planner's tool asks first for")
    fun toolAsksFirst(table: DataTable) {
        val schema = request().getValue("tools").jsonArray.first().jsonObject.getValue("input_schema").jsonObject
        val expected = table.asMaps().map { it.getValue("property") }
        assertEquals(expected, schema.getValue("properties").jsonObject.keys.take(expected.size))
        assertEquals(expected, schema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
    }

    @Then("the move's reason is {string}")
    fun reason(why: String) = assertEquals(why, checkNotNull(move).reason)

    @Then("the planner's system prompt says")
    fun systemSays(table: DataTable) {
        val system = request().getValue("system").jsonArray.first().jsonObject.getValue("text").jsonPrimitive.content
            .replace(Regex("\\s+"), " ")
        table.asMaps().map { it.getValue("part") }.forEach { assertTrue(it in system, "no \"$it\" in:\n$system") }
    }

    @Then("the planner's request caches its system prompt and tool")
    fun caches() {
        val request = request()
        assertNotNull(request.getValue("system").jsonArray.first().jsonObject["cache_control"])
        assertNotNull(request.getValue("tools").jsonArray.first().jsonObject["cache_control"])
    }

    private fun request(): JsonObject = Json.parseToJsonElement(
        ctx.transport.requests.last { it.url == ClaudeReasoner.ENDPOINT }.body.orEmpty(),
    ).jsonObject

    /** A move as the feature says it. */
    private fun words(move: Move): String = when (move) {
        is Move.Act -> "act \"${move.words}\" for sentence ${move.sentence}" +
            if (move.opens) ", opening a block" else ""
        is Move.Back -> "back to \"${move.to}\""
        is Move.Wait -> "wait until \"${move.relation}\" is offered"
        is Move.Check -> "check ${move.facts}"
        is Move.Done -> "sentence ${move.sentence} done"
        is Move.Finish -> "finished"
        is Move.Stop -> "stopped: ${move.why}"
        is Move.Clarify -> "a question: ${move.question}"
    }

    private companion object {
        const val OK = 200
    }
}
