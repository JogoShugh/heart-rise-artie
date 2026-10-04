package com.artie.gardenbed.eventstore

import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.repository.GardenBedAggregateRepository
import com.artie.gardenbed.repository.GardenBedSnapshotRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * EventStore on real Postgres. Found by ARTIE's live scenarios: the first
 * snapshot (event 5) made the act fail with 500 after its event was
 * already committed, so the client saw a failure for a write that happened.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EventStorePostgresTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired lateinit var store: EventStore
    @Autowired lateinit var beds: GardenBedAggregateRepository
    @Autowired lateinit var snapshots: GardenBedSnapshotRepository
    @Autowired lateinit var db: DatabaseClient

    private suspend fun newBed(): Long = beds.save(GardenBedAggregate(label = "test")).id!!

    private suspend fun water(bedId: Long, sequenceNo: Int) =
        store.append(GardenBedEvent.CellWatered(bedId, sequenceNo, 1))

    @Test
    fun `appending past the snapshot interval takes and then refreshes a snapshot`() = runBlocking {
        val bed = newBed()
        store.append(GardenBedEvent.SeedSown(bed, 1, "basil", 2))
        for (seq in 2..EventStore.SNAPSHOT_INTERVAL * 2) water(bed, seq)

        assertEquals(EventStore.SNAPSHOT_INTERVAL * 2, snapshots.findByGardenBedId(bed)?.sequenceNo)
        assertEquals(EventStore.SNAPSHOT_INTERVAL * 2, store.currentState(bed, "test").lastSequenceNo)
    }

    /** The snapshot is a disposable cache. If writing it fails, the event is
     *  already committed, so the append must still report success. */
    @Test
    fun `a failing snapshot write does not fail an append that committed`() = runBlocking {
        val bed = newBed()
        store.append(GardenBedEvent.SeedSown(bed, 1, "basil", 2))
        for (seq in 2 until EventStore.SNAPSHOT_INTERVAL) water(bed, seq)
        db.sql("ALTER TABLE garden_bed_snapshot RENAME TO garden_bed_snapshot_hidden").then().block()
        try {
            water(bed, EventStore.SNAPSHOT_INTERVAL)
        } finally {
            db.sql("ALTER TABLE garden_bed_snapshot_hidden RENAME TO garden_bed_snapshot").then().block()
        }
        assertEquals(EventStore.SNAPSHOT_INTERVAL, store.currentState(bed, "test").lastSequenceNo)
    }

    @Test
    fun `a sequence number already taken is a concurrent append`() = runBlocking {
        val bed = newBed()
        store.append(GardenBedEvent.SeedSown(bed, 1, "basil", 2))
        assertThrows(ConcurrentAppendException::class.java) { runBlocking { water(bed, 1) } }
        Unit
    }
}
