package com.artie.heart.fixtures

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Offers
import com.artie.heart.Options
import com.artie.heart.Pick
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

/** One player on a team's sheet. */
data class MatchPlayer(val number: Int, val name: String, val fouledOut: Boolean = false)

/** A match, whose forms take their choices from its own state and from another resource. */
data class Match(
    val id: Long,
    val phase: String,
    val level: String,
    val home: List<MatchPlayer>,
    val away: List<MatchPlayer>,
    val version: Int = 1,
)

/** The foul act's input: a team, then one of that team's players. */
@Schema(title = "Foul")
data class Foul(
    @field:NotNull @field:Schema(title = "Team", allowableValues = ["home", "away"])
    val team: String? = null,
    @field:NotNull @field:Schema(title = "Player", description = "The shirt number of the player who fouled")
    val playerNumber: Int? = null,
)

/** The input of assigning an official, one of the officials the league lists. */
@Schema(title = "Assign an official")
data class AssignOfficial(
    @field:NotNull @field:Schema(title = "Official")
    val official: String? = null,
)

/**
 * A match, declared with choices from state (HAL-SCHEMA-FORMS-HR Rule 8): a foul's
 * player comes from the chosen team's players in the match itself, and an
 * official comes from the league's officials, another resource.
 */
object Matches {
    /** The league's officials, as the other resource serves them. */
    val officials: List<Map<String, Any>> = listOf(
        mapOf("name" to "Kim", "level" to "senior", "available" to true),
        mapOf("name" to "Lou", "level" to "senior", "available" to false),
        mapOf("name" to "Max", "level" to "junior", "available" to true),
    )

    fun profile() = Profile(
        name = "league",
        title = "Matches",
        doc = "The acts a match offers.",
        relations = listOf(
            Relation("foul", AlpsType.UNSAFE, "Record a foul", "Records a foul by one player.", "Offered while playing."),
            Relation(
                "assign-official",
                AlpsType.UNSAFE,
                "Assign an official",
                "Assigns one of the league's available officials.",
                "Offered while playing.",
            ),
        ),
    )

    fun type(): ResourceType<Match, String> = ResourceType(
        collection = "matches",
        profile = profile(),
        acts = listOf(
            Act.of("foul", "foul", Foul::class.java).withOptions(
                mapOf(
                    "playerNumber" to Options.fromState(
                        "/{team}/players",
                        Pick(value = "number", prompt = "name", where = mapOf("fouledOut" to false), maxItems = 1),
                    ),
                ),
            ),
            Act.of("assignOfficial", "assign-official", AssignOfficial::class.java).withOptions(
                mapOf(
                    "official" to Options.fromLink(
                        href = "/officials{?level}",
                        templatePointers = mapOf("level" to "/level"),
                        items = "/officials",
                        pick = Pick(value = "name", prompt = "name", where = mapOf("available" to true), maxItems = 1),
                        resolve = { state, _ -> officials.filter { it["level"] == (state as Match).level } },
                    ),
                ),
            ),
        ),
        offers = Offers(
            keys = listOf("PLAYING", "FINISHED"),
            offered = { phase -> if (phase == "PLAYING") listOf("foul", "assignOfficial") else emptyList() },
            whyAbsent = { _, _ -> "The match is over." },
        ),
        state = StateView(
            keyOf = { it.phase },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = { match ->
                linkedMapOf(
                    "id" to match.id,
                    "phase" to match.phase,
                    "level" to match.level,
                    "home" to mapOf("players" to match.home.map(::player)),
                    "away" to mapOf("players" to match.away.map(::player)),
                )
            },
            keyField = "phase",
        ),
    )

    private fun player(p: MatchPlayer) = linkedMapOf("number" to p.number, "name" to p.name, "fouledOut" to p.fouledOut)
}
