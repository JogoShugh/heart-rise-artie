package com.artie.gardenbed.bdd

import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedStatus
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import kotlinx.coroutines.runBlocking

/** Puts beds into a known state by appending the events that lead there. */
class BedSteps(private val beds: InMemoryBeds, private val store: InMemoryEventStore) {

    @Before
    fun resetStorage() {
        beds.reset()
        store.reset()
    }

    @Given("bed {long} is {word}")
    fun bedIs(id: Long, status: String) = runBlocking {
        beds.add(id)
        val events = when (GardenBedStatus.valueOf(status)) {
            GardenBedStatus.EMPTY -> emptyList()
            GardenBedStatus.PLANTED -> listOf(sown(id))
            // 500 ml adds 100 growth points, the harvest threshold.
            GardenBedStatus.READY_TO_HARVEST -> listOf(sown(id), GardenBedEvent.CellWatered(id, 2, 500))
        }
        events.forEach { store.append(it) }
    }

    /** A concurrent writer: appends straight to the store, bypassing HTTP. */
    @Given("someone else waters bed {long} with {int} ml")
    fun someoneElseWaters(id: Long, milliliters: Int) = runBlocking {
        store.append(GardenBedEvent.CellWatered(id, store.nextSequenceNo(id), milliliters))
    }

    private fun sown(id: Long) = GardenBedEvent.SeedSown(id, 1, "basil", 2)
}
