package com.artie.gardenbed.repository

import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEventRow
import com.artie.gardenbed.domain.GardenBedSnapshotRow
import io.r2dbc.postgresql.codec.Json
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface GardenBedAggregateRepository : CoroutineCrudRepository<GardenBedAggregate, Long>

interface GardenBedEventRepository : CoroutineCrudRepository<GardenBedEventRow, Long> {

    /** The full history, in order -- the actual replay input. Flow, not
     *  a suspend-returned List, so a long history streams rather than
     *  loading entirely into memory before folding begins. */
    fun findByGardenBedIdOrderBySequenceNoAsc(gardenBedId: Long): Flow<GardenBedEventRow>

    /** Only events after a given sequence number -- what you fold on
     *  top of a snapshot, instead of the bed's entire history. */
    fun findByGardenBedIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(
        gardenBedId: Long,
        sequenceNo: Int
    ): Flow<GardenBedEventRow>

    /** The current tip of the log for a bed -- used to compute the
     *  next sequence number before an append. Returns null for a bed
     *  with no events yet (still EMPTY, nothing has happened to it). */
    suspend fun findFirstByGardenBedIdOrderBySequenceNoDesc(gardenBedId: Long): GardenBedEventRow?
}

interface GardenBedSnapshotRepository : CoroutineCrudRepository<GardenBedSnapshotRow, Long> {
    suspend fun findByGardenBedId(gardenBedId: Long): GardenBedSnapshotRow?

    /** Insert or replace a bed's one snapshot. Not save(): the row's @Id is
     *  the bed id, always set, so Spring Data R2DBC treats every save as an
     *  UPDATE, which fails for a bed's first snapshot. */
    @Modifying
    @Query(
        """
        INSERT INTO garden_bed_snapshot (garden_bed_id, sequence_no, state, snapshotted_at)
        VALUES (:gardenBedId, :sequenceNo, :state, now())
        ON CONFLICT (garden_bed_id) DO UPDATE
        SET sequence_no = EXCLUDED.sequence_no, state = EXCLUDED.state, snapshotted_at = EXCLUDED.snapshotted_at
        """
    )
    suspend fun upsert(gardenBedId: Long, sequenceNo: Int, state: Json): Int
}
