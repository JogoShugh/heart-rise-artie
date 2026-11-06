package com.artie.basketball.declaration

import com.artie.basketball.domain.GameState
import com.artie.basketball.domain.Phase
import com.artie.basketball.domain.Rules
import com.artie.basketball.domain.Side
import com.artie.heart.Act
import com.artie.heart.Finding
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.Options
import com.artie.heart.Pick
import com.artie.heart.ResourceType
import com.artie.heart.StateView

/** A game, declared once for the HEART library. */
object Games {

    /**
     * HAL-SCHEMA-FORMS-HR Rule 8: the player is one of the chosen team's players in
     * the game itself, at /{team}/players, leaving out those who have fouled out.
     */
    private val playerOfTheTeam = mapOf(
        "playerNumber" to Options.fromState(
            "/{team}/players",
            Pick(value = "number", prompt = "name", where = mapOf("fouledOut" to false), maxItems = 1),
        ),
    )

    private val inPlay = listOf(
        "scoreBasket", "freeThrow", "recordFoul", "turnover", "callTimeout", "endPeriod", "correctScore",
    )

    val type = ResourceType(
        collection = "games",
        profile = BasketballProfile.profile,
        acts = listOf(
            Act.of("tipOff", "tip-off", TipOff::class.java),
            Act.of("scoreBasket", "score-basket", ScoreBasket::class.java).withOptions(playerOfTheTeam),
            Act.of("freeThrow", "free-throw", FreeThrow::class.java).withOptions(playerOfTheTeam),
            Act.of("recordFoul", "record-foul", RecordFoul::class.java).withOptions(playerOfTheTeam),
            Act.of("turnover", "turnover", Turnover::class.java),
            Act.of("callTimeout", "call-timeout", CallTimeout::class.java),
            Act.of("resumePlay", "resume-play", NoInput::class.java),
            Act.of("endPeriod", "end-period", NoInput::class.java),
            Act.of("startPeriod", "start-period", NoInput::class.java),
            Act.of("finalWhistle", "final-whistle", NoInput::class.java),
            Act.of("correctScore", "correct-score", CorrectScore::class.java),
        ),
        offers = Offers(
            keys = Phase.entries,
            offered = { phase ->
                when (phase) {
                    Phase.SCHEDULED -> listOf("tipOff")
                    Phase.LIVE -> inPlay
                    Phase.TIMEOUT -> listOf("resumePlay", "correctScore")
                    Phase.BREAK -> listOf("startPeriod")
                    Phase.FULL_TIME -> listOf("finalWhistle")
                    Phase.FINAL -> emptyList()
                }
            },
            whyAbsent = BasketballProfile::whyAbsent,
        ),
        state = StateView<GameState, Phase>(
            keyOf = { it.phase },
            idOf = { it.id },
            versionOf = { it.version },
            bodyOf = ::body,
        ),
        finding = Finding(
            noun = "game",
            search = Named("searchGames", "search-games"),
            lookup = Named("getGame", "get-game"),
            nameDescription = "Part of the game's name; case is ignored",
            idTitle = "Game id",
            idDescription = "The game's number",
        ),
    )

    val layers = Layers(type)

    private fun body(game: GameState): Map<String, Any?> {
        val setup = checkNotNull(game.setup)
        return linkedMapOf(
            "id" to game.id,
            "label" to setup.label,
            "phase" to game.phase.name,
            "period" to game.period,
            "periods" to Rules.PERIODS,
            "possession" to game.possession,
            Side.HOME to team(game, Side.HOME),
            Side.AWAY to team(game, Side.AWAY),
            "scoringPlays" to game.plays.map {
                mapOf(
                    "sequenceNo" to it.sequenceNo, "team" to it.team, "playerNumber" to it.playerNumber,
                    "points" to it.points, "takenBack" to it.takenBack,
                )
            },
        )
    }

    private fun team(game: GameState, side: String): Map<String, Any?> {
        val team = checkNotNull(game.setup).team(side)
        return linkedMapOf(
            "name" to team.name,
            "score" to game.score(side),
            "teamFouls" to game.teamFouls(side),
            "inBonus" to game.inBonus(side),
            "players" to team.roster.toSortedMap().map { (number, name) ->
                val stats = game.player(side, number)
                mapOf(
                    "number" to number, "name" to name, "points" to stats.points,
                    "fouls" to stats.fouls, "fouledOut" to game.fouledOut(side, number),
                )
            },
        )
    }
}
