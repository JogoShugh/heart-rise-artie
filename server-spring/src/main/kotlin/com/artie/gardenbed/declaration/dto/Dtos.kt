package com.artie.gardenbed.declaration.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

// Each act's input class is the one source of its JSON Schema: heart-core
// generates the schema from these annotations (Jakarta Validation for the
// constraints, OpenAPI @Schema for the words) and checks bodies against it.
//
// Request DTOs use `var`, not `val`: Spring HATEOAS marks a property with
// no setter as readOnly, and HAL-FORMS then tells clients the field can't
// be filled in (Spring HATEOAS #1310). Guarded by RequestDtoMetadataTest.
@Schema(title = "Sow Seed")
data class SowSeedRequest(
    @field:NotBlank
    @field:Schema(title = "Seed", description = "The kind of seed to sow", examples = ["basil"])
    var seedType: String = "",

    @field:Min(1) @field:Max(10)
    @field:Schema(title = "Depth", description = "How deep to sow, in centimetres", examples = ["2"])
    var depthCm: Int? = null
)

@Schema(title = "Water Cell")
data class WaterCellRequest(
    @field:NotNull @field:Min(1) @field:Max(1000)
    @field:Schema(
        title = "Water per cell",
        description = "Millilitres of water poured into each cell",
        examples = ["250"],
    )
    var milliliters: Int = 0
)

/** Harvesting takes no input. */
@Schema(title = "Harvest")
class HarvestRequest

data class HarvestResult(
    val yieldGrams: Int,
    val cropName: String
)

/** The plain-data view of a bed's current state, independent of which
 *  hypermedia dialect wraps it. */
data class GardenBedDto(
    val id: Long,
    val label: String,
    val status: String,
    val cropName: String?,
    val growthPoints: Int,
    val moistureLevel: Int
)
