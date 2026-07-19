package com.artie.gardenbed.domain

import io.r2dbc.postgresql.codec.Json
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * The raw persisted row for one event. `payload` is typed as
 * `io.r2dbc.postgresql.codec.Json` -- the r2dbc-postgresql driver's
 * own wrapper for JSONB columns, which it round-trips natively without
 * needing a custom Spring Data converter. Build one via `Json.of(text)`,
 * read the raw text back via `.asString()`.
 *
 * MODERATE CONFIDENCE FLAG: this is a commonly-documented pattern for
 * r2dbc-postgresql + JSONB, but not compiled here. If `Json` isn't
 * resolvable at this package, or Spring Data R2DBC doesn't accept it
 * as a mapped column type out of the box for your resolved driver
 * version, the fallback is a custom `@ReadingConverter`/
 * `@WritingConverter` pair converting `String` <-> `Json` explicitly,
 * registered via `R2dbcCustomConversions` in a `@Configuration` class
 * -- more boilerplate, but a very standard Spring Data escape hatch if
 * the native mapping doesn't Just Work.
 */
@Table("garden_bed_event")
data class GardenBedEventRow(
    @Id
    val id: Long? = null,
    val gardenBedId: Long,
    val sequenceNo: Int,
    val eventType: String,
    val payload: Json,
    val occurredAt: Instant = Instant.now()
)

@Table("garden_bed_snapshot")
data class GardenBedSnapshotRow(
    @Id
    val gardenBedId: Long,
    val sequenceNo: Int,
    val state: Json,
    val snapshottedAt: Instant = Instant.now()
)
