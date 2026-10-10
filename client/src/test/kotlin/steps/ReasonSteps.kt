package steps

import heart.ArtieAgent
import heart.ClaudeReasoner
import heart.Decision
import heart.HeartResponse
import heart.LinkOffer
import heart.Offer
import heart.Reasoner
import heart.Selection
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A reasoner that returns a scripted selection and records what it was shown. */
class ScriptedReasoner(private val answer: Selection) : Reasoner {
    val shown = mutableListOf<List<Offer>>()
    val shownLinks = mutableListOf<List<LinkOffer>>()

    override fun reason(intent: String, state: JsonObject, offers: List<Offer>, links: List<LinkOffer>): Selection {
        shown += offers
        shownLinks += links
        return answer
    }
}

/** Steps for SPEC §6.8 / §6.10.7: the Reason step and the checks around it. */
class ReasonSteps(private val ctx: ClientContext) {
    private var reasoner: Reasoner? = null
    private var decision: Decision? = null
    private var selection: Selection? = null

    @Given("the reasoner will select {string} with {}")
    fun willSelect(affordance: String, fields: String) {
        reasoner = ScriptedReasoner(Selection.Act(affordance, Json.parseToJsonElement(fields).jsonObject))
    }

    @Given("the reasoner will follow the {string} link titled {string}")
    fun willFollow(rel: String, title: String) {
        reasoner = ScriptedReasoner(Selection.Follow(rel, title))
    }

    @Given("the reasoner will ask {string}")
    fun willAsk(question: String) {
        reasoner = ScriptedReasoner(Selection.Clarify(question))
    }

    @Given("the reasoner is Claude, using the ANTHROPIC_API_KEY environment variable")
    fun claude() {
        val key = checkNotNull(System.getenv("ANTHROPIC_API_KEY")) { "ANTHROPIC_API_KEY is not set" }
        reasoner = ClaudeReasoner(heart.HttpTransport(), key)
    }

    @When("the agent handles {string} at {string}")
    fun handle(intent: String, url: String) {
        decision = agent().handle(intent, ctx.url(url))
        ctx.client.current?.let { ctx.current = it }
        ctx.requestsAfterEnter = ctx.transport.requests.count { it.method == "GET" }
    }

    @When("the agent handles {string} at {string} without acting")
    fun decide(intent: String, url: String) {
        decision = agent().decide(intent, ctx.url(url))
    }

    @Then("the reasoner was shown the offers {string} and no URI or method")
    fun shown(names: String) {
        val offers = (reasoner as ScriptedReasoner).shown.single()
        assertEquals(names.split(',').map { it.trim() }, offers.map { it.name })
        val text = offers.joinToString { it.toString() }
        assertFalse(text.contains("/beds/") || text.contains("POST"), "offers leaked a URI or method: $text")
    }

    @Then("the reasoner was shown the links {string} and no URI")
    fun shownLinks(expected: String) {
        val links = (reasoner as ScriptedReasoner).shownLinks.single()
        assertEquals(expected, links.joinToString { "${it.rel}: ${it.title}" })
        assertFalse(links.toString().contains("/beds"), "links leaked a URI: $links")
    }

    @Then("the agent followed the link to {string}")
    fun followed(url: String) = assertEquals(url, assertIs<Decision.Followed>(decision).result.url)

    @Then("the agent acted")
    fun acted() = assertIs<Decision.Acted>(decision)

    @Then("the agent rejected the selection because {string}")
    fun rejected(reason: String) {
        val rejected = assertIs<Decision.Rejected>(decision)
        assertTrue(rejected.reasons.any { it.contains(reason) }, "reasons: ${rejected.reasons}")
    }

    @Then("the agent asked {string}")
    fun asked(question: String) = assertEquals(question, assertIs<Decision.Asked>(decision).question)

    @Then("the reasoner selected {string} with milliliters {int}")
    fun selectedLive(affordance: String, milliliters: Int) {
        val chosen = assertIs<Selection.Act>(assertIs<Decision.Selected>(decision).selection)
        assertEquals(affordance, chosen.affordance)
        assertEquals(milliliters, chosen.fields["milliliters"]?.jsonPrimitive?.int)
    }

    // ---- the Claude reasoner, against the scripted transport ----

    @Given("the Claude API will answer with a tool call selecting {string} with {}")
    fun claudeAnswers(affordance: String, fields: String) {
        val body = """{"id":"msg_1","type":"message","role":"assistant","content":[""" +
            """{"type":"tool_use","id":"t1","name":"select","input":""" +
            """{"action":"act","affordance":"$affordance","fields":$fields}}],"stop_reason":"tool_use"}"""
        ctx.transport.respond("POST", ClaudeReasoner.ENDPOINT, HeartResponse(OK, emptyMap(), body))
    }

    @When("the Claude reasoner reasons about {string} over the manifest of {string}")
    fun claudeReasons(intent: String, url: String) {
        val synced = ctx.client.enter(ctx.url(url))
        val offers = synced.affordances.values.map { Offer(it.rel, it.schema) }
        val links = synced.linksByRel.flatMap { (rel, list) -> list.map { LinkOffer(rel, it.title) } }
        selection = ClaudeReasoner(ctx.transport, "test-key").reason(intent, synced.state, offers, links)
    }

    @Then("the Claude request has a system prompt and one {string} tool, both marked for prompt caching")
    fun claudePrefix(tool: String) {
        val body = claudeRequestBody()
        val system = body.getValue("system").jsonArray.last().jsonObject
        assertTrue(system.containsKey("cache_control"), "system: $system")
        val tools = body.getValue("tools").jsonArray
        assertEquals(1, tools.size)
        assertEquals(tool, tools.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertTrue(tools.single().jsonObject.containsKey("cache_control"), "tool: ${tools.single()}")
    }

    @Then("the Claude request's last message carries the state, the offers and the intent, in that order")
    fun claudeOrder() {
        val text = claudeRequestBody().getValue("messages").jsonArray.last().jsonObject
            .getValue("content").jsonArray.joinToString { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
        val positions = listOf("<state>", "<offers>", "<intent>").map { text.indexOf(it) }
        assertTrue(positions.all { it >= 0 } && positions == positions.sorted(), "order $positions in: $text")
    }

    @Given("the Claude API will answer with a tool call following the {string} link titled {string}")
    fun claudeFollows(rel: String, title: String) {
        val body = """{"id":"msg_1","type":"message","role":"assistant","content":[""" +
            """{"type":"tool_use","id":"t1","name":"select","input":""" +
            """{"action":"follow","rel":"$rel","title":"$title"}}],"stop_reason":"tool_use"}"""
        ctx.transport.respond("POST", ClaudeReasoner.ENDPOINT, HeartResponse(OK, emptyMap(), body))
    }

    @Then("the Claude request shows the link {string} titled {string} and no href")
    fun claudeShowsLink(rel: String, title: String) {
        val raw = ctx.transport.requests.last { it.url == ClaudeReasoner.ENDPOINT }.body.orEmpty()
        assertTrue(raw.contains(rel) && raw.contains(title), raw)
        assertFalse(raw.contains("/beds/1") || raw.contains("/beds/2") || raw.contains("href"), raw)
    }

    @Then("the Claude reasoner chose to follow the {string} link titled {string}")
    fun claudeFollowed(rel: String, title: String) =
        assertEquals(Selection.Follow(rel, title), selection)

    @Then("the Claude request contains no URI from the manifest")
    fun claudeNoUri() {
        val raw = ctx.transport.requests.last { it.url == ClaudeReasoner.ENDPOINT }.body.orEmpty()
        assertFalse(raw.contains("/beds/2/actions") || raw.contains("garden.test"), raw)
    }

    @Then("the Claude reasoner selected {string} with {}")
    fun claudeSelected(affordance: String, fields: String) {
        val chosen = assertIs<Selection.Act>(selection)
        assertEquals(affordance, chosen.affordance)
        assertEquals(Json.parseToJsonElement(fields), chosen.fields)
    }

    private fun agent() = ArtieAgent(ctx.client, checkNotNull(reasoner) { "no reasoner given" })

    private fun claudeRequestBody(): JsonObject =
        Json.parseToJsonElement(ctx.transport.requests.last { it.url == ClaudeReasoner.ENDPOINT }.body.orEmpty())
            .jsonObject

    private companion object {
        const val OK = 200
    }
}
