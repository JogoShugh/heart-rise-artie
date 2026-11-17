package com.artie.tolls.declaration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// Each act's input. Its JSON Schema is generated from these annotations, and
// the body is checked against that same schema before the act runs.

private const val MAX_FUNDS = 500L
private const val TAG_LENGTH = 6
private const val TAG = "The tag's number, as printed on it"

@Schema(title = "Add funds")
data class AddFunds(
    @field:NotNull @field:Min(1) @field:Max(MAX_FUNDS)
    @field:Schema(title = "Amount", description = "Whole dollars to add", examples = ["25"])
    val amount: Int? = null,
)

@Schema(title = "Add a tag")
data class AddTag(
    @field:NotNull @field:Size(min = TAG_LENGTH, max = TAG_LENGTH)
    @field:Schema(title = "Tag", description = TAG, examples = ["T-1003"])
    val tag: String? = null,
)

@Schema(title = "Charge a trip")
data class RecordTrip(
    @field:NotNull @field:Schema(title = "Tag", description = "The tag the plaza read")
    val tag: String? = null,
    @field:NotNull
    @field:Schema(
        title = "Plaza",
        description = "Where the trip was made",
        allowableValues = ["harbor-bridge", "north-tunnel", "turnpike-exit-9"],
    )
    val plaza: String? = null,
    @field:NotNull
    @field:Schema(
        title = "Vehicle class",
        description = "The vehicle's class, by its axles",
        allowableValues = ["car", "two-axle-truck", "five-axle-truck"],
    )
    val vehicleClass: String? = null,
    @field:NotNull
    @field:Schema(title = "Time", description = "Peak or off-peak", allowableValues = ["peak", "off-peak"])
    val time: String? = null,
)

@Schema(title = "Report a tag lost")
data class ReportTagLost(
    @field:NotNull @field:Schema(title = "Tag", description = "The tag lost")
    val tag: String? = null,
)

@Schema(title = "Dispute a toll")
data class DisputeToll(
    @field:NotNull @field:Min(1)
    @field:Schema(title = "Entry", description = "The statement entry of the trip to dispute", examples = ["3"])
    val sequenceNo: Int? = null,
)

/** Closing the account takes no input. */
@Schema(title = "No input")
class NoInput
