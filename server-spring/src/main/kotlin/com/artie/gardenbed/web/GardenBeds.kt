package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.web.dto.HarvestRequest
import com.artie.gardenbed.web.dto.SowSeedRequest
import com.artie.gardenbed.web.dto.WaterCellRequest
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
 */
object GardenBeds {

    val type = ResourceType(
        collection = "beds",
        profile = GardenProfile.profile,
        acts = listOf(
            Act.of("sowSeed", "sow-seed", SowSeedRequest::class.java),
            Act.of("waterCell", "water-cell", WaterCellRequest::class.java),
            Act.of("harvest", "harvest", HarvestRequest::class.java),
        ),
        offers = Offers(
            keys = GardenBedStatus.entries,
            offered = { status ->
                when (status) {
                    GardenBedStatus.EMPTY -> listOf("sowSeed")
                    GardenBedStatus.PLANTED -> listOf("waterCell")
                    GardenBedStatus.READY_TO_HARVEST -> listOf("harvest")
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
                    "status" to it.status.name,
                    "cropName" to it.cropName,
                    "growthPoints" to it.growthPoints,
                    "moistureLevel" to it.moistureLevel,
                )
            },
        ),
    )

    val layers = Layers(type)
}
