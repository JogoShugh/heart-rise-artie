package com.artie.gardenbed.eventstore

import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.service.GardenBedService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The garden on real Postgres, after migrations V1 (the garden's own event
 * tables, with seed beds) and V2 (the move to the HEART event store). The
 * seed beds' events must have moved with their numbers and times, so each
 * bed folds to the state it had before, and the next act continues the
 * same stream. The store's own rules (a number taken twice is refused) are
 * proven by heart-eventsourcing's features against Postgres.
 */
@SpringBootTest(properties = ["heart.coap.port=-1"])
@Testcontainers(disabledWithoutDocker = true)
class MigrationPostgresTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    @Autowired
    lateinit var service: GardenBedService

    private suspend fun statusAndVersion(id: Long) = service.currentState(id).let { it.status to it.lastSequenceNo }

    @Test
    fun `the seed beds fold to the states they had before the move, and the next act continues the stream`() =
        runBlocking {
            assertEquals(GardenBedStatus.EMPTY to 0, statusAndVersion(1))
            assertEquals(GardenBedStatus.PLANTED to 3, statusAndVersion(2))
            assertEquals(GardenBedStatus.READY_TO_HARVEST to 3, statusAndVersion(3))
            assertEquals("bed-2", service.currentState(2).label)

            service.waterCell(2, 1)

            assertEquals(GardenBedStatus.PLANTED to 4, statusAndVersion(2))
            assertEquals(listOf(1, 2, 3, 4), service.history(2).map { it.sequenceNo })
        }
}
