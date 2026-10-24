package com.artie.gardenbed.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * The aggregate root, and NOTHING else. No status, no cropName, no
 * growthPoints -- every one of those is derived by folding this bed's
 * events (see GardenBedProjector), never stored here. This table
 * exists only to give a bed a stable identity to hang events off of,
 * with what never changes once it is built: its name and size in feet.
 */
@Table("garden_bed")
data class GardenBedAggregate(
    @Id
    val id: Long? = null,
    val label: String,
    val widthFt: Int,
    val lengthFt: Int,
    val createdAt: Instant = Instant.now()
)
