package com.artie.gardenbed.declaration.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// Each act's input class is the one source of its JSON Schema: heart-core
// generates the schema from these annotations (Jakarta Validation for the
// constraints, OpenAPI @Schema for the words) and checks bodies against it.
// Both dialects' forms are rendered from that schema.
@Schema(title = "Sow Seed")
data class SowSeedRequest(
    @field:NotBlank
    @field:Schema(title = "Seed", description = "The kind of seed to sow", examples = ["basil"])
    val seedType: String = "",

    @field:Min(1) @field:Max(10)
    @field:Schema(title = "Depth", description = "How deep to sow, in centimetres", examples = ["2"])
    val depthCm: Int? = null
)

@Schema(title = "Water Cell")
data class WaterCellRequest(
    @field:NotNull @field:Min(1) @field:Max(1000)
    @field:Schema(
        title = "Water per cell",
        description = "Millilitres of water poured into each cell",
        examples = ["250"],
    )
    val milliliters: Int = 0
)

@Schema(title = "Build Bed")
data class BuildBedRequest(
    @field:NotBlank
    @field:Schema(title = "Name", description = "What the bed is called", examples = ["Herbs"])
    val label: String = "",

    @field:NotNull @field:Min(1) @field:Max(MAX_FEET)
    @field:Schema(title = "Width", description = "How wide the bed is, in feet", examples = ["4"])
    val widthFt: Int = 0,

    @field:NotNull @field:Min(1) @field:Max(MAX_FEET)
    @field:Schema(title = "Length", description = "How long the bed is, in feet", examples = ["8"])
    val lengthFt: Int = 0,
)

private const val MAX_FEET = 100L

/** Harvesting takes no input. */
@Schema(title = "Harvest")
class HarvestRequest

/** Thawing takes no input: the fleece is all it needs. */
@Schema(title = "Thaw")
class ThawRequest

/**
 * A bed as its plain body reads: the shape of its state, from which the
 * OpenAPI description's Bed schema is generated. Its status is a closed
 * enum of the statuses this garden declares.
 */
@Schema(title = "Bed")
data class Bed(
    @field:NotNull @field:Schema(title = "Bed id", description = "The bed's number")
    val id: Long? = null,
    @field:NotNull @field:Schema(title = "Name", description = "What the bed is called")
    val label: String? = null,
    @field:NotNull @field:Schema(title = "Width", description = "How wide the bed is, in feet")
    val widthFt: Int? = null,
    @field:NotNull @field:Schema(title = "Length", description = "How long the bed is, in feet")
    val lengthFt: Int? = null,
    @field:NotNull @field:Schema(title = "Status", description = "What the bed holds now")
    val status: String? = null,
    @field:Schema(title = "Crop", description = "What is planted, if anything", nullable = true)
    val cropName: String? = null,
    @field:NotNull @field:Schema(title = "Growth", description = "How far the crop has grown, to 100")
    val growthPoints: Int? = null,
    @field:NotNull @field:Schema(title = "Moisture", description = "How moist the soil is, to 100")
    val moistureLevel: Int? = null,
)

data class HarvestResult(
    val yieldGrams: Int,
    val cropName: String
)
