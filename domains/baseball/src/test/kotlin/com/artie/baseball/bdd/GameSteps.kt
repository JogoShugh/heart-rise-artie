package com.artie.baseball.bdd

import com.artie.baseball.domain.GAMES
import com.artie.baseball.domain.GameEvent
import com.artie.baseball.domain.GameSetup
import com.artie.baseball.domain.Phase
import com.artie.baseball.domain.Player
import com.artie.baseball.domain.Team
import com.artie.baseball.store.InMemoryGameDirectory
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The scorekeeper's view of a game, over HTTP through the HEART library. Games are brought to a point by real
 * plays only, recorded in order: runners get on by hits, outs are batters fielded out.
 */
class GameSteps(
    private val client: WebTestClient,
    private val games: InMemoryGameDirectory,
    private val events: InMemoryEventStore,
) {
    private lateinit var last: EntityExchangeResult<ByteArray>
    private var state: JsonNode = ObjectMapper().createObjectNode()

    @Given("game {long}, the Cubs at home to the Bears")
    fun aGame(id: Long) = runBlocking {
        games.clear()
        events.forget(GAMES.stream)
        games.add(GameSetup(id, "Cubs v Bears", CUBS, BEARS))
    }

    @Given("game {long} is in the {word} of inning {int}, nobody out")
    fun inning(id: Long, half: String, inning: Int) = inningWithOuts(id, half, inning, 0)

    @Given("game {long} is in the {word} of inning {int}, {int} out")
    fun inningWithOuts(id: Long, half: String, inning: Int, outs: Int) = runBlocking {
        record(id, GameEvent.PlayBall)
        val halves = (inning - 1) * 2 + if (half == "bottom") 1 else 0
        repeat(halves) { playOutHalf(id) }
        repeat(outs) { record(id, GameEvent.Hit("fielded-out")) }
    }

    @Given("runners are on {}")
    fun runners(bases: String) = runBlocking {
        val hits = checkNotNull(TO_LOAD[bases.trim()]) { "no plays that put runners on $bases" }
        hits.forEach { record(1, GameEvent.Hit(it)) }
    }

    @Given("the batter has missed {int} pitches")
    fun pitches(count: Int) = runBlocking {
        repeat(count) { record(1, GameEvent.Pitched("missed")) }
    }

    @Given("the away team has hit {int} home runs this half")
    fun homeRuns(count: Int) = runBlocking {
        repeat(count) { record(1, GameEvent.Hit("home-run")) }
    }

    /** With nobody out: two batters fielded out and the rest single, so no half ends before the last bats. */
    @Given("the away team's last batter is up")
    fun lastBatterUp() = runBlocking {
        repeat(2) { record(1, GameEvent.Hit("fielded-out")) }
        repeat(BEARS.lineup.size - 3) { record(1, GameEvent.Hit("single")) }
    }

    @When("the scorer opens game {long}")
    fun open(id: Long) {
        read(id)
    }

    @When("the scorer records {string} on game {long} with {}")
    fun act(path: String, id: Long, body: String) {
        val etag = read(id).responseHeaders.eTag
        last = client.post().uri("/games/$id/actions/$path")
            .header(HttpHeaders.ACCEPT, HAL_JSON).header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.IF_MATCH, checkNotNull(etag))
            .bodyValue(body).exchange().expectBody().returnResult()
        if (last.status.is2xxSuccessful) state = json(last)
    }

    @When("the top and bottom of inning {int} are played out")
    fun playOut(inning: Int) = runBlocking {
        repeat(2) { playOutHalf(1) }
        assertEquals(inning + 1, events.state(GAMES, 1).inning)
    }

    @Then("the game offers exactly")
    fun offers(table: DataTable) {
        assertEquals(table.asMaps().map { it.getValue("act") }.toSet(), forms())
    }

    @Then("the game offers nothing")
    fun offersNothing() = assertEquals(emptySet<String>(), forms())

    @Then("{string} is absent because {string}")
    fun absent(key: String, reason: String) {
        val href = state.path("_links").path("hr:unavailable").path("href").asText()
        val explanation = json(client.get().uri(href).exchange().expectBody().returnResult())
        val profile = state.path("_links").path("curies").first { it.path("name").asText() == key.substringBefore(':') }
        val rel = profile.path("href").asText().replace("{rel}", key.substringAfter(':'))
        val entry = explanation.path("unavailable").firstOrNull { it.path("rel").asText() == rel }
        assertEquals(reason, checkNotNull(entry) { "$key is not explained: $explanation" }.path("reason").asText())
    }

    @Then("game {long} is {string} in the {string} of inning {int} with the {string} team batting")
    fun phase(id: Long, phase: String, half: String, inning: Int, side: String) {
        read(id)
        val actual = listOf("phase", "half", "inning", "batting").map { state.path(it).asText() }
        assertEquals(listOf(phase, half, inning.toString(), side), actual)
    }

    @Then("game {long} is {string}")
    fun phaseOnly(id: Long, phase: String) {
        read(id)
        assertEquals(phase, state.path("phase").asText())
    }

    @Then("the batter up is {word} player {int}")
    fun batterUp(side: String, number: Int) {
        read(1)
        assertEquals(side, state.path("batting").asText())
        assertEquals(number, state.path("batterUp").path("number").asInt(), state.path("batterUp").toString())
    }

    @Then("the bases are {}")
    fun bases(bases: String) {
        read(1)
        val occupied = listOf("first", "second", "third").filter { !state.path("bases").path(it).isNull }
        val expected = if (bases.trim() == "empty") emptyList() else bases.split(',').map { it.trim() }
        assertEquals(expected, occupied, state.path("bases").toString())
    }

    @Then("the score is home {int}, away {int}")
    fun score(home: Int, away: Int) {
        read(1)
        assertEquals(home to away, state.path("home").path("runs").asInt() to state.path("away").path("runs").asInt())
    }

    @Then("{word} player {int} has {int} hit(s)")
    fun hits(side: String, number: Int, hits: Int) {
        read(1)
        val player = state.path(side).path("lineup").first { it.path("number").asInt() == number }
        assertEquals(hits, player.path("hits").asInt(), player.toString())
    }

    @Then("there is {int} out")
    fun outs(outs: Int) {
        read(1)
        assertEquals(outs, state.path("outs").asInt())
    }

    @Then("the batter has had {int} pitches")
    fun pitchesSoFar(count: Int) {
        read(1)
        assertEquals(count, state.path("pitches").asInt())
    }

    @Then("the away team has {int} runs this half")
    fun runsThisHalf(runs: Int) {
        read(1)
        assertEquals(runs, state.path("runsThisHalf").asInt())
    }

    @Then("the away team's line reads {int} in inning {int}")
    fun line(runs: Int, inning: Int) {
        read(1)
        assertEquals(runs, state.path("away").path("line").path(inning - 1).asInt(), state.path("away").toString())
    }

    @Then("the game refuses it by a game rule at {string}")
    fun refusedByRule(pointer: String) {
        assertEquals(422, last.status.value(), String(last.responseBody ?: ByteArray(0)))
        assertEquals(PROBLEM_TYPES + "rule-refused", json(last).path("type").asText())
        assertEquals(listOf(pointer), json(last).path("violations").map { it.path("pointer").asText() }.distinct())
    }

    private suspend fun playOutHalf(id: Long) {
        while (events.state(GAMES, id).phase == Phase.PLAY) record(id, GameEvent.Hit("fielded-out"))
        record(id, GameEvent.NextHalf)
    }

    private fun read(id: Long): EntityExchangeResult<ByteArray> =
        client.get().uri("/games/$id").header(HttpHeaders.ACCEPT, HAL_JSON).exchange().expectBody().returnResult()
            .also { state = json(it) }

    private fun forms(): Set<String> = state.path("_forms").fieldNames().asSequence().toSet()

    private suspend fun record(id: Long, event: GameEvent) {
        events.append(GAMES, id, events.nextSequenceNo(GAMES, id), event)
    }

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private companion object {
        const val HAL_JSON = "application/hal+json"

        /** Hits that leave runners on just these bases, with nobody scoring. */
        val TO_LOAD = mapOf(
            "first" to listOf("single"),
            "second" to listOf("double"),
            "third" to listOf("triple"),
            "first, second" to listOf("single", "single"),
            "first, third" to listOf("double", "single"),
            "second, third" to listOf("single", "double"),
            "first, second, third" to listOf("single", "single", "single"),
        )

        val CUBS = Team(
            "Cubs",
            listOf(
                Player(1, "Avery"),
                Player(3, "Jordan"),
                Player(4, "Riley"),
                Player(6, "Sam"),
                Player(7, "Casey"),
                Player(9, "Logan"),
                Player(11, "Parker"),
                Player(13, "Reese"),
            ),
        )
        val BEARS = Team(
            "Bears",
            listOf(
                Player(2, "Taylor"),
                Player(5, "Morgan"),
                Player(8, "Quinn"),
                Player(10, "Drew"),
                Player(12, "Jamie"),
                Player(14, "Miller"),
                Player(17, "Harper"),
                Player(21, "Ellis"),
            ),
        )
    }
}
