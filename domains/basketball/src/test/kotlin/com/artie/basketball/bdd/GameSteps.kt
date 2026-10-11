package com.artie.basketball.bdd

import com.artie.basketball.domain.GAMES
import com.artie.basketball.domain.GameEvent
import com.artie.basketball.domain.GameSetup
import com.artie.basketball.domain.Team
import com.artie.basketball.store.InMemoryGameDirectory
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/** The scorer's view of a game, over HTTP through the HEART library. */
class GameSteps(
    private val client: WebTestClient,
    private val games: InMemoryGameDirectory,
    private val events: InMemoryEventStore,
) {
    private lateinit var last: EntityExchangeResult<ByteArray>
    private var state: JsonNode = ObjectMapper().createObjectNode()

    @Given("game {long}, the Eagles at home to the Marauders")
    fun aGame(id: Long) = runBlocking {
        games.clear()
        events.forget(GAMES.stream)
        games.add(GameSetup(id, "Eagles v Marauders", EAGLES, MARAUDERS))
    }

    @Given("game {long} is live in period {int} with the {string} team in possession")
    fun live(id: Long, period: Int, side: String) = runBlocking {
        record(id, GameEvent.TippedOff(side))
        repeat(period - 1) {
            record(id, GameEvent.PeriodEnded)
            record(id, GameEvent.PeriodStarted)
        }
    }

    @Given("home player {int} has {int} personal fouls")
    fun fouls(number: Int, count: Int) = runBlocking {
        repeat(count) { record(1, GameEvent.FoulRecorded("home", number, "personal")) }
    }

    @Given("the {string} team committed {int} fouls this half")
    fun teamFouls(side: String, count: Int) = runBlocking {
        val roster = if (side == "home") EAGLES.roster.keys else MARAUDERS.roster.keys
        repeat(count) { i -> record(1, GameEvent.FoulRecorded(side, roster.elementAt(i % roster.size), "personal")) }
    }

    @When("the scorer opens game {long}")
    fun open(id: Long) {
        read(id)
    }

    @Given("the scorer recorded {string} on game {long} with {}")
    @When("the scorer records {string} on game {long} with {}")
    fun act(path: String, id: Long, body: String) {
        val etag = read(id).responseHeaders.eTag
        last = client.post().uri("/games/$id/actions/$path")
            .header(HttpHeaders.ACCEPT, HAL_JSON).header(HttpHeaders.CONTENT_TYPE, "application/json")
            .header(HttpHeaders.IF_MATCH, checkNotNull(etag))
            .bodyValue(body).exchange().expectBody().returnResult()
        if (last.status.is2xxSuccessful) state = json(last)
    }

    @When("the scorer takes back game {long}'s play number {int}")
    fun takeBack(id: Long, sequenceNo: Int) = act("correct-score", id, """{"sequenceNo": $sequenceNo}""")

    @Then("the game offers exactly {string}")
    fun offers(keys: String) {
        assertEquals(keys.split(",").map { it.trim() }.toSet(), forms())
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

    @Then("game {long} is {string} in period {int} with the {string} team in possession")
    fun phase(id: Long, phase: String, period: Int, side: String) {
        read(id)
        val actual = listOf("phase", "period", "possession").map { state.path(it).asText() }
        assertEquals(listOf(phase, period.toString(), side), actual)
    }

    @Then("game {long} is {string}")
    fun phaseOnly(id: Long, phase: String) {
        read(id)
        assertEquals(phase, state.path("phase").asText())
    }

    @Then("the score is home {int}, away {int}")
    fun score(home: Int, away: Int) {
        read(1)
        assertEquals(home to away, state.path("home").path("score").asInt() to state.path("away").path("score").asInt())
    }

    @Then("home player {int} has {int} points")
    fun points(number: Int, points: Int) = assertEquals(points, player("home", number).path("points").asInt())

    @Then("home player {int} has fouled out")
    fun fouledOut(number: Int) = assertTrue(player("home", number).path("fouledOut").asBoolean())

    @Then("the {string} team has possession")
    fun possession(side: String) {
        read(1)
        assertEquals(side, state.path("possession").asText())
    }

    @Then("the {string} team is in the bonus")
    fun bonus(side: String) {
        read(1)
        assertTrue(state.path(side).path("inBonus").asBoolean(), state.path(side).toString())
    }

    @Then("the {string} team has {int} fouls this half")
    fun foulsThisHalf(side: String, count: Int) {
        read(1)
        assertEquals(count, state.path(side).path("teamFouls").asInt())
    }

    @Then("the game refuses it as invalid at {string}")
    fun invalid(pointer: String) {
        assertEquals(422, last.status.value(), String(last.responseBody ?: ByteArray(0)))
        assertEquals(listOf(pointer), json(last).path("violations").map { it.path("pointer").asText() }.distinct())
    }

    private fun read(id: Long): EntityExchangeResult<ByteArray> =
        client.get().uri("/games/$id").header(HttpHeaders.ACCEPT, HAL_JSON).exchange().expectBody().returnResult()
            .also { state = json(it) }

    private fun player(side: String, number: Int): JsonNode {
        read(1)
        return state.path(side).path("players").first { it.path("number").asInt() == number }
    }

    private fun forms(): Set<String> = state.path("_forms").fieldNames().asSequence().toSet()

    private suspend fun record(id: Long, event: GameEvent) {
        events.append(GAMES, id, events.nextSequenceNo(GAMES, id), event)
    }

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private companion object {
        const val HAL_JSON = "application/hal+json"
        val EAGLES = Team("Eagles", mapOf(4 to "Avery", 11 to "Jordan", 23 to "Riley", 30 to "Sam", 32 to "Casey"))
        val MARAUDERS = Team(
            "Marauders",
            mapOf(3 to "Taylor", 7 to "Morgan", 15 to "Quinn", 21 to "Drew", 34 to "Jamie"),
        )
    }
}
