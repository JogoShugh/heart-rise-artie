package com.artie.gardenbed.bdd

import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedStatus
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import kotlinx.coroutines.runBlocking

/** Puts beds into a known state by appending the events that lead there. */
class BedSteps(private val beds: InMemoryBeds, private val store: BedEventLog) {

    @Before
    fun resetStorage() {
        beds.reset()
        store.reset()
    }

    @Given("bed {long} is {word}")
    fun bedIs(id: Long, status: String) = putBed(beds, store, id, GardenBedStatus.valueOf(status))
}

/** Adds bed [id] and appends the events that lead to [status]. */
fun putBed(beds: InMemoryBeds, store: BedEventLog, id: Long, status: GardenBedStatus) = runBlocking {
    beds.add(id)
    val sown = GardenBedEvent.SeedSown("basil", 2)
    val events = when (status) {
        GardenBedStatus.EMPTY -> emptyList()
        GardenBedStatus.PLANTED -> listOf(sown)
        // 500 ml adds 100 growth points, the harvest threshold.
        GardenBedStatus.READY_TO_HARVEST -> listOf(sown, GardenBedEvent.CellWatered(500))
        // Only a garden with frost (Tuesday) has frozen beds.
        GardenBedStatus.FROZEN -> listOf(sown, GardenBedEvent.BedFrozen("basil"))
    }
    events.forEach { store.append(id, it) }
}
