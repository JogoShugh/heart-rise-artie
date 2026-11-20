package com.artie.heart.eventsourcing.bdd

import com.artie.heart.ConcurrentWriteException
import com.artie.heart.eventsourcing.EventSourcing
import com.artie.heart.eventsourcing.EventStore
import com.artie.heart.eventsourcing.InMemoryEventStore
import com.artie.heart.eventsourcing.PostgresEventStore
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Duration
import java.time.Instant

/** An event-sourced lamp: its events, and the state they fold into. */
sealed interface LampEvent {
    data object SwitchedOn : LampEvent
    data object SwitchedOff : LampEvent
    data class Dimmed(val brightness: Int) : LampEvent
}

data class LampState(val id: Long, val status: String, val brightness: Int, val version: Int)

class StoreSteps {
    private lateinit var store: EventStore
    private var lamps = lamps(snapshotEvery = 5)
    private var outcomes: List<Result<Any>> = emptyList()
    private var state: LampState? = null

    @Given("the {word} event store")
    fun theStore(kind: String) {
        store = when (kind) {
            "in-memory" -> InMemoryEventStore()
            else -> PostgresEventStore(Postgres.client)
        }
        runBlocking { store.forget(lamps.stream) }
    }

    @Given("the {word} event store, snapshotting every {int} events")
    fun theStoreSnapshotting(kind: String, every: Int) {
        lamps = lamps(snapshotEvery = every)
        theStore(kind)
    }

    @Given("lamp {long} records SwitchedOn")
    fun recordsOn(id: Long) = runBlocking { record(id, LampEvent.SwitchedOn) }

    @When("lamp {long} records these events")
    fun recordsThese(id: Long, table: DataTable) = runBlocking {
        table.asMaps().forEach { row ->
            val event = when (row.getValue("event")) {
                "SwitchedOn" -> LampEvent.SwitchedOn
                else -> LampEvent.Dimmed(row.getValue("brightness").toInt())
            }
            record(id, event)
        }
    }

    @When("two writers both record an event as lamp {long}'s number {int}")
    fun twoWriters(id: Long, number: Int) = runBlocking {
        outcomes = listOf(LampEvent.Dimmed(10), LampEvent.Dimmed(20)).map { event ->
            async { runCatching { store.append(lamps, id, number, event) } }
        }.awaitAll()
    }

    @Then("exactly one is stored, and the other is refused as a concurrent write")
    fun exactlyOne() {
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.exceptionOrNull() is ConcurrentWriteException })
    }

    @Given("lamp {long} recorded:")
    fun recordedAgo(id: Long, table: DataTable) = runBlocking {
        table.asMaps().forEachIndexed { index, row ->
            val event = when (row.getValue("event")) {
                "SwitchedOn" -> LampEvent.SwitchedOn
                else -> LampEvent.Dimmed(row.getValue("brightness").toInt())
            }
            store.append(lamps, id, index + 1, event, daysAgo(row.getValue("days ago").toInt()))
        }
    }

    @When("lamp {long}'s state is read as of {int} hours ago")
    fun asOf(id: Long, hours: Int) = runBlocking {
        state = store.state(lamps, id, asOf = Instant.now().minus(Duration.ofHours(hours.toLong())))
    }

    @When("lamp {long} records SwitchedOn and then {int} Dims, the last to {int}")
    fun manyDims(id: Long, count: Int, last: Int) = runBlocking {
        record(id, LampEvent.SwitchedOn)
        (count - 1 downTo 0).forEach { left -> record(id, LampEvent.Dimmed(last + left)) }
    }

    @Then("lamp {long}'s state is {string} at brightness {int}, version {int}")
    fun stateIs(id: Long, status: String, brightness: Int, version: Int) = runBlocking {
        val read = state ?: store.state(lamps, id)
        assertEquals(LampState(id, status, brightness, version), read)
    }

    @Then("lamp {long}'s version is {int}")
    fun versionIs(id: Long, version: Int) = runBlocking {
        assertEquals(version, store.state(lamps, id).version)
    }

    @Then("lamp {long}'s history is")
    fun history(id: Long, table: DataTable) = runBlocking {
        val history = store.history(lamps, id)
            .map { mapOf("number" to "${it.sequenceNo}", "type" to lamps.typeOf(it.event)) }
        assertEquals(table.asMaps(), history)
    }

    @Then("the state read from the latest snapshot equals the state folded from the first event")
    fun snapshotAgrees() = runBlocking {
        assertEquals(store.state(lamps, 7, fromSnapshot = false), store.state(lamps, 7))
    }

    private suspend fun record(id: Long, event: LampEvent) {
        store.append(lamps, id, store.nextSequenceNo(lamps, id), event)
    }

    private fun daysAgo(days: Int): Instant = Instant.now().minus(Duration.ofDays(days.toLong()))

    private companion object {
        fun lamps(snapshotEvery: Int) = EventSourcing<LampEvent, LampState>(
            stream = "lamp",
            types = mapOf(
                "SwitchedOn" to LampEvent.SwitchedOn::class.java,
                "SwitchedOff" to LampEvent.SwitchedOff::class.java,
                "Dimmed" to LampEvent.Dimmed::class.java,
            ),
            stateClass = LampState::class.java,
            empty = { id -> LampState(id, "OFF", 0, 0) },
            apply = { state, recorded ->
                when (val event = recorded.event) {
                    LampEvent.SwitchedOn -> state.copy(status = "ON")
                    LampEvent.SwitchedOff -> state.copy(status = "OFF")
                    is LampEvent.Dimmed -> state.copy(brightness = event.brightness)
                }.copy(version = recorded.sequenceNo)
            },
            snapshotEvery = snapshotEvery,
        )
    }
}

/** One Postgres for the whole run, with the store's schema applied. */
object Postgres {
    private val container = PostgreSQLContainer("postgres:16-alpine").apply { start() }

    val client: DatabaseClient by lazy {
        val url = "r2dbc:postgresql://${container.username}:${container.password}@" +
            "${container.host}:${container.firstMappedPort}/${container.databaseName}"
        DatabaseClient.create(ConnectionFactories.get(url)).also { client ->
            runBlocking { PostgresEventStore.createSchema(client) }
        }
    }
}
