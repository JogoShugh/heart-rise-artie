package com.artie.gardenbed.eventstore

import com.artie.gardenbed.domain.GardenBedEvent
import com.artie.gardenbed.domain.GardenBedProjector
import com.artie.gardenbed.domain.GardenBedSnapshotRow
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.repository.GardenBedEventRepository
import com.artie.gardenbed.repository.GardenBedSnapshotRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import com.artie.heart.ConcurrentWriteException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.time.Instant

/** Thrown when an append's expected sequence number no longer matches
 *  reality -- someone else appended first. Maps to 412 Precondition
 *  Failed at the controller, the direct server-side analogue of
 *  ARTIE's own client-side 428-on-stale-hash discipline. */
class ConcurrentAppendException(message: String) : ConcurrentWriteException(message)

@Component
class EventStore(
    private val events: GardenBedEventRepository,
    private val snapshots: GardenBedSnapshotRepository,
    private val codec: EventCodec,
    private val objectMapper: ObjectMapper
) {
    companion object {
        /** Take a fresh snapshot every N events. Small on purpose for
         *  this demo (in a real system with real event volume, this
         *  would be much larger -- hundreds or thousands). */
        const val SNAPSHOT_INTERVAL = 5

        private val log = LoggerFactory.getLogger(EventStore::class.java)
    }

    suspend fun nextSequenceNo(bedId: Long): Int =
        (events.findFirstByGardenBedIdOrderBySequenceNoDesc(bedId)?.sequenceNo ?: 0) + 1

    /**
     * Appends one event, enforcing optimistic concurrency via the
     * database's own UNIQUE(garden_bed_id, sequence_no) constraint --
     * not via an application-level compare-then-write, which would
     * have a race window between the check and the write. If two
     * callers both try to append sequence 6 for the same bed, exactly
     * one INSERT succeeds; the other hits the constraint and this
     * throws ConcurrentAppendException.
     *
     * The R2DBC driver's constraint violation does arrive as Spring's
     * `DataIntegrityViolationException`: verified against real Postgres
     * by EventStorePostgresTest.
     */
    suspend fun append(event: GardenBedEvent) {
        try {
            events.save(codec.toRow(event))
        } catch (e: DataIntegrityViolationException) {
            throw ConcurrentAppendException(
                "Bed ${event.gardenBedId}: sequence ${event.sequenceNo} was already taken " +
                    "by a concurrent write. Re-read current state and retry."
            )
        }
        // The event is committed. The snapshot is a disposable cache, so a
        // failure here must not turn a write that happened into an error the
        // client would read as "not written" (and might retry).
        try {
            maybeSnapshot(event.gardenBedId)
        } catch (e: DataAccessException) {
            log.warn("snapshot for bed ${event.gardenBedId} skipped; state still folds from events", e)
        }
    }

    /** Loads current state: snapshot (if any) + fold only the events
     *  after it, instead of the bed's entire history from event 1. */
    suspend fun currentState(bedId: Long, label: String): GardenBedState {
        val snapshot = snapshots.findByGardenBedId(bedId)
        val seed = snapshot?.let { readSnapshotState(it).copy(label = label) } ?: GardenBedState.empty(bedId, label)
        val startAfter = snapshot?.sequenceNo ?: 0

        val tailEvents = events.findByGardenBedIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(bedId, startAfter)
            .map(codec::decode)
            .toList()

        return GardenBedProjector.project(seed, tailEvents)
    }

    /**
     * Point-in-time reconstruction. Only uses the snapshot as a
     * starting point if the snapshot itself was taken AT OR BEFORE the
     * requested instant -- otherwise the snapshot already reflects
     * events from AFTER the point being asked about, and using it
     * would silently produce a wrong (too-advanced) answer. When in
     * doubt, this falls back to folding the full history from empty,
     * which is always correct, just slower.
     */
    suspend fun stateAt(bedId: Long, label: String, asOf: Instant): GardenBedState {
        val snapshot = snapshots.findByGardenBedId(bedId)
        val canUseSnapshot = snapshot != null && !snapshot.snapshottedAt.isAfter(asOf)

        val seed = if (canUseSnapshot) readSnapshotState(snapshot!!).copy(label = label) else GardenBedState.empty(bedId, label)
        val startAfter = if (canUseSnapshot) snapshot!!.sequenceNo else 0

        val relevantEvents = events.findByGardenBedIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(bedId, startAfter)
            .map(codec::decode)
            .toList()
            .filter { !it.occurredAt.isAfter(asOf) }

        return GardenBedProjector.project(seed, relevantEvents)
    }

    /** The FULL history, decoded -- for a "show me everything that
     *  ever happened to this bed" endpoint, not used by the state
     *  queries above (which deliberately avoid loading the full log
     *  when a snapshot can shortcut it). */
    suspend fun fullHistory(bedId: Long): List<GardenBedEvent> =
        events.findByGardenBedIdOrderBySequenceNoAsc(bedId).map(codec::decode).toList()

    private suspend fun maybeSnapshot(bedId: Long) {
        val latest = events.findFirstByGardenBedIdOrderBySequenceNoDesc(bedId) ?: return
        if (latest.sequenceNo % SNAPSHOT_INTERVAL != 0) return

        // Recompute fully from scratch for the snapshot write itself --
        // simplest correct thing to do here, and only happens once
        // every SNAPSHOT_INTERVAL events, not on every append.
        val allEvents = events.findByGardenBedIdOrderBySequenceNoAsc(bedId).map(codec::decode).toList()
        val state = GardenBedProjector.project(GardenBedState.empty(bedId, ""), allEvents)

        snapshots.upsert(bedId, latest.sequenceNo, objectMapper.toJson(state))
    }

    private fun readSnapshotState(row: GardenBedSnapshotRow): GardenBedState =
        objectMapper.fromJson(row.state, GardenBedState::class.java) as GardenBedState
}
