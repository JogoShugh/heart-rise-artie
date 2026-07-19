package com.artie.gardenbed.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class SowSeedRequest(
    @field:NotBlank
    val seedType: String = "",

    @field:Min(1) @field:Max(10)
    val depthCm: Int? = null
)

data class WaterCellRequest(
    @field:Min(1) @field:Max(1000)
    val milliliters: Int = 0
)

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
