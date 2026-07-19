package com.artie.gardenbed.repository

import com.artie.gardenbed.domain.GardenBedAggregate
import com.artie.gardenbed.domain.GardenBedEventRow
import com.artie.gardenbed.domain.GardenBedSnapshotRow
import kotlinx.coroutines.flow.Flow
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
}
