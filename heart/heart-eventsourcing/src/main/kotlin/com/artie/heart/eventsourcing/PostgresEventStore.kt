package com.artie.heart.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import io.r2dbc.postgresql.codec.Json
import io.r2dbc.spi.Readable
import kotlinx.coroutines.flow.toList
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.await
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.flow
import java.time.Instant

/**
 * The event store on Postgres, through R2DBC. The UNIQUE constraint on
 * (stream, stream_id, sequence_no) is the concurrency control: of two
 * writers appending the same number, exactly one INSERT succeeds. Its schema
 * is [SCHEMA], which a server applies in its own migrations.
 */
class PostgresEventStore(
    private val db: DatabaseClient,
    mapper: ObjectMapper = defaultMapper(),
) : JsonEventStore(mapper) {

    override suspend fun insert(stream: String, id: Long, event: StoredEvent) {
        try {
            db.sql(INSERT_EVENT)
                .bind("stream", stream).bind("id", id).bind("seq", event.sequenceNo).bind("type", event.type)
                .bind("payload", Json.of(event.payload)).bind("at", event.occurredAt)
                .await()
        } catch (_: DataIntegrityViolationException) {
            throw taken(stream, id, event.sequenceNo)
        }
    }

    override suspend fun events(stream: String, id: Long, after: Int): List<StoredEvent> =
        db.sql(SELECT_EVENTS)
            .bind("stream", stream).bind("id", id).bind("after", after)
            .map { row ->
                StoredEvent(
                    int(row, "sequence_no"), text(row, "event_type"), json(row, "payload"), at(row, "occurred_at"),
                )
            }
            .flow().toList()

    /** A row mapping cannot answer null, so a stream with no events reads as 0 and is turned back into null. */
    override suspend fun lastSequenceNo(stream: String, id: Long): Int? =
        db.sql(SELECT_LAST)
            .bind("stream", stream).bind("id", id)
            .map { row -> int(row, "last") }
            .awaitOneOrNull()
            ?.takeIf { it > 0 }

    override suspend fun snapshot(stream: String, id: Long): StoredSnapshot? =
        db.sql(SELECT_SNAPSHOT)
            .bind("stream", stream).bind("id", id)
            .map { row -> StoredSnapshot(int(row, "sequence_no"), json(row, "state"), at(row, "snapshotted_at")) }
            .awaitOneOrNull()

    override suspend fun saveSnapshot(stream: String, id: Long, snapshot: StoredSnapshot) {
        db.sql(UPSERT_SNAPSHOT)
            .bind("stream", stream).bind("id", id).bind("seq", snapshot.sequenceNo)
            .bind("state", Json.of(snapshot.state)).bind("at", snapshot.takenAt)
            .await()
    }

    override suspend fun forget(stream: String) {
        db.sql("DELETE FROM heart_event WHERE stream = :stream").bind("stream", stream).await()
        db.sql("DELETE FROM heart_snapshot WHERE stream = :stream").bind("stream", stream).await()
    }

    companion object {
        /** The store's tables. A server applies this in its own migrations, or with [createSchema]. */
        val SCHEMA: String = checkNotNull(PostgresEventStore::class.java.getResource("schema.sql")).readText()

        /** Applies [SCHEMA]; each statement is idempotent. */
        suspend fun createSchema(db: DatabaseClient) {
            SCHEMA.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
                .split(';').map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { db.sql(it).await() }
        }

        private const val INSERT_EVENT =
            "INSERT INTO heart_event (stream, stream_id, sequence_no, event_type, payload, occurred_at) " +
                "VALUES (:stream, :id, :seq, :type, :payload, :at)"
        private const val SELECT_EVENTS =
            "SELECT sequence_no, event_type, payload, occurred_at FROM heart_event " +
                "WHERE stream = :stream AND stream_id = :id AND sequence_no > :after ORDER BY sequence_no"
        private const val SELECT_LAST =
            "SELECT COALESCE(max(sequence_no), 0) AS last FROM heart_event WHERE stream = :stream AND stream_id = :id"
        private const val SELECT_SNAPSHOT =
            "SELECT sequence_no, state, snapshotted_at FROM heart_snapshot WHERE stream = :stream AND stream_id = :id"
        private const val UPSERT_SNAPSHOT =
            "INSERT INTO heart_snapshot (stream, stream_id, sequence_no, state, snapshotted_at) " +
                "VALUES (:stream, :id, :seq, :state, :at) ON CONFLICT (stream, stream_id) DO UPDATE " +
                "SET sequence_no = EXCLUDED.sequence_no, state = EXCLUDED.state, " +
                "snapshotted_at = EXCLUDED.snapshotted_at"

        private fun int(row: Readable, column: String): Int = checkNotNull(row.get(column, Integer::class.java)).toInt()
        private fun text(row: Readable, column: String): String = checkNotNull(row.get(column, String::class.java))
        private fun at(row: Readable, column: String): Instant = checkNotNull(row.get(column, Instant::class.java))
        private fun json(row: Readable, column: String): String =
            checkNotNull(row.get(column, Json::class.java)).asString()
    }
}
