package com.artie.basketball.declaration

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

// Each act's input. Its JSON Schema is generated from these annotations, and
// the body is checked against that same schema before the act runs.

private const val TEAM = "Which team: home or away"
private const val PLAYER = "The player's shirt number"
private const val MAX_SHIRT = 99L
private const val TWO_POINTS = 2L
private const val THREE_POINTS = 3L
private const val HOME = "home"
private const val AWAY = "away"

@Schema(title = "Tip off")
data class TipOff(
    @field:NotNull
    @field:Schema(title = "Possession", description = "The team that won the tip", allowableValues = [HOME, AWAY])
    val possession: String? = null,
)

@Schema(title = "Score a basket")
data class ScoreBasket(
    @field:NotNull @field:Schema(title = "Team", description = TEAM, allowableValues = [HOME, AWAY])
    val team: String? = null,
    @field:NotNull @field:Min(0) @field:Max(MAX_SHIRT)
    @field:Schema(title = "Player", description = PLAYER, examples = ["23"])
    val playerNumber: Int? = null,
    @field:NotNull @field:Min(TWO_POINTS) @field:Max(THREE_POINTS)
    @field:Schema(title = "Points", description = "2 for a basket inside the arc, 3 from beyond it", examples = ["2"])
    val points: Int? = null,
)

@Schema(title = "Free throw")
data class FreeThrow(
    @field:NotNull @field:Schema(title = "Team", description = TEAM, allowableValues = [HOME, AWAY])
    val team: String? = null,
    @field:NotNull @field:Min(0) @field:Max(MAX_SHIRT)
    @field:Schema(title = "Player", description = PLAYER, examples = ["7"])
    val playerNumber: Int? = null,
    @field:NotNull @field:Schema(title = "Made", description = "Whether the free throw went in")
    val made: Boolean? = null,
)

@Schema(title = "Record a foul")
data class RecordFoul(
    @field:NotNull
    @field:Schema(
        title = "Team",
        description = "The team of the player who fouled",
        allowableValues = [HOME, AWAY],
    )
    val team: String? = null,
    @field:NotNull @field:Min(0) @field:Max(MAX_SHIRT)
    @field:Schema(title = "Player", description = "The shirt number of the player who fouled", examples = ["11"])
    val playerNumber: Int? = null,
    @field:NotNull
    @field:Schema(
        title = "Kind",
        description = "The kind of foul",
        allowableValues = ["personal", "shooting", "technical"],
    )
    val kind: String? = null,
)

@Schema(title = "Turnover")
data class Turnover(
    @field:NotNull
    @field:Schema(
        title = "Team",
        description = "The team that lost the ball",
        allowableValues = [HOME, AWAY],
    )
    val team: String? = null,
    @field:NotNull
    @field:Schema(
        title = "Reason",
        description = "How the ball was lost",
        allowableValues = ["steal", "out-of-bounds", "travel", "double-dribble"],
    )
    val reason: String? = null,
)

@Schema(title = "Call a timeout")
data class CallTimeout(
    @field:NotNull @field:Schema(title = "Team", description = "The team calling it", allowableValues = [HOME, AWAY])
    val team: String? = null,
)

@Schema(title = "Take back a scoring play")
data class CorrectScore(
    @field:NotNull @field:Min(1)
    @field:Schema(
        title = "Play",
        description = "The sequence number of the scoring play to take back",
        examples = ["2"],
    )
    val sequenceNo: Int? = null,
)

/** Resuming play, ending or starting a period, and the final whistle take no input. */
@Schema(title = "No input")
class NoInput
