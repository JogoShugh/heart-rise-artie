package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.heart.Act
import com.artie.heart.Layers
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
            Act("sowSeed", "sow-seed", SOW_SEED),
            Act("waterCell", "water-cell", WATER_CELL),
            Act("harvest", "harvest", HARVEST),
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

private val SOW_SEED = """
    {
      "title": "Sow Seed",
      "type": "object",
      "properties": {
        "seedType": {
          "type": "string",
          "title": "Seed",
          "description": "The kind of seed to sow",
          "examples": ["basil"]
        },
        "depthCm": {
          "type": "number", "minimum": 1, "maximum": 10,
          "title": "Depth",
          "description": "How deep to sow, in centimetres",
          "examples": [2]
        }
      },
      "required": ["seedType"]
    }
""".trimIndent()

private val WATER_CELL = """
    {
      "title": "Water Cell",
      "type": "object",
      "properties": {
        "milliliters": {
          "type": "integer", "minimum": 1, "maximum": 1000,
          "title": "Water per cell",
          "description": "Millilitres of water poured into each cell",
          "examples": [250]
        }
      },
      "required": ["milliliters"]
    }
""".trimIndent()

private val HARVEST = """
    {
      "title": "Harvest",
      "type": "object",
      "properties": {}
    }
""".trimIndent()
