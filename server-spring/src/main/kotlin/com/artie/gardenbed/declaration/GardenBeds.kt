package com.artie.gardenbed.declaration

import com.artie.gardenbed.declaration.dto.Bed
import com.artie.gardenbed.declaration.dto.BedWithSoilMoisture
import com.artie.gardenbed.declaration.dto.BuildBedRequest
import com.artie.gardenbed.declaration.dto.WaterCellLimitedRequest
import com.artie.gardenbed.declaration.dto.WaterCellMethodRequest
import com.artie.gardenbed.declaration.dto.HarvestRequest
import com.artie.gardenbed.declaration.dto.SowSeedRequest
import com.artie.gardenbed.declaration.dto.ThawRequest
import com.artie.gardenbed.declaration.dto.WaterCellRequest
import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.Act
import com.artie.heart.Finding
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.ResourceType
import com.artie.heart.StateView

/**
 * The garden bed, declared once for heart-core: its acts and their schemas,
 * which acts each status offers, and how a bed's state reads. heart-core
 * renders every layer from this ([layers]).
 *
 * [frost] is Tuesday (From OpenAPI to HEART, part three): a hard frost can
 * freeze a planted bed, which then offers only thaw. Without it the
 * declaration is Monday's, and every byte it renders is as it was.
 */
object GardenBeds {

    fun type(frost: Boolean = false, change: GardenChange? = null) = ResourceType(
        collection = "beds",
        profile = GardenProfile.profile(frost, change),
        acts = listOfNotNull(
            Act.of("sowSeed", "sow-seed", SowSeedRequest::class.java),
            water(change),
            Act.of("harvest", "harvest", HarvestRequest::class.java),
            Act.of("thaw", "thaw", ThawRequest::class.java).takeIf { frost },
        ),
        bookmarkActs = listOf(Act.of("buildBed", "build-bed", BuildBedRequest::class.java)),
        offers = Offers(
            keys = GardenBedStatus.entries.filter { frost || it != GardenBedStatus.FROZEN },
            offered = { status ->
                when (status) {
                    GardenBedStatus.EMPTY -> listOf("sowSeed")
                    GardenBedStatus.PLANTED -> listOf("waterCell")
                    GardenBedStatus.READY_TO_HARVEST -> listOf("harvest")
                    GardenBedStatus.FROZEN -> listOf("thaw")
                }
            },
            whyAbsent = GardenProfile::whyAbsent,
        ),
        finding = Finding(
            noun = "bed",
            search = Named("searchBeds", "search-beds"),
            lookup = Named("getBed", "get-bed"),
            nameDescription = "Part of the bed's name; case is ignored",
            idTitle = "Bed id",
            idDescription = "The bed's number",
        ),
        state = StateView<GardenBedState, GardenBedStatus>(
            keyOf = { it.status },
            idOf = { it.id },
            versionOf = { it.lastSequenceNo },
            bodyOf = {
                linkedMapOf(
                    "id" to it.id,
                    "label" to it.label,
                    "widthFt" to it.widthFt,
                    "lengthFt" to it.lengthFt,
                    "status" to it.status.name,
                    "cropName" to it.cropName,
                    "growthPoints" to it.growthPoints,
                    (if (change == GardenChange.RENAME_FIELD) "soilMoisture" else "moistureLevel") to it.moistureLevel,
                )
            },
            bodyClass = if (change == GardenChange.RENAME_FIELD) BedWithSoilMoisture::class.java else Bed::class.java,
            keyField = "status",
        ),
    )

    fun layers(frost: Boolean = false, change: GardenChange? = null) = Layers(type(frost, change))

    /** The water act as Monday declares it, or as one Wednesday change alters it. */
    private fun water(change: GardenChange?): Act = Act.of(
        name = "waterCell",
        relation = if (change == GardenChange.RENAME_ACT) "irrigate" else "water-cell",
        input = when (change) {
            GardenChange.TIGHTER_LIMIT -> WaterCellLimitedRequest::class.java
            GardenChange.NEW_REQUIRED_INPUT -> WaterCellMethodRequest::class.java
            else -> WaterCellRequest::class.java
        },
        path = if (change == GardenChange.MOVE_URL) "water" else "water-cell",
    )
}
