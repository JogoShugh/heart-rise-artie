package com.artie.baseball.declaration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

// Each act's input. Its JSON Schema is generated from these annotations, and
// the body is checked against that same schema before the act runs.

@Schema(title = "Pitch")
data class Pitch(
    @field:NotNull
    @field:Schema(
        title = "Result",
        description = "Missed (swung at or taken), or fouled off",
        allowableValues = ["missed", "foul"],
    )
    val result: String? = null,
)

@Schema(title = "Ball in play")
data class Hit(
    @field:NotNull
    @field:Schema(
        title = "Result",
        description = "How far the batter got, or fielded out",
        allowableValues = ["single", "double", "triple", "home-run", "fielded-out"],
    )
    val result: String? = null,
)

@Schema(title = "Runner out")
data class RunnerOut(
    @field:NotNull
    @field:Schema(
        title = "Base",
        description = "The base the runner was put out on",
        allowableValues = ["first", "second", "third"],
    )
    val base: String? = null,
)

/** Play ball, the next half-inning and the end of the game take no input. */
@Schema(title = "No input")
class NoInput
