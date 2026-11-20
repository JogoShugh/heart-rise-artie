package com.artie.baseball.declaration

import com.artie.baseball.domain.Bases
import com.artie.baseball.domain.GameState
import com.artie.baseball.domain.Phase
import com.artie.baseball.domain.Rules
import com.artie.baseball.domain.Side
import com.artie.heart.Act
import com.artie.heart.Finding
import com.artie.heart.Layers
import com.artie.heart.Named
import com.artie.heart.Offers
import com.artie.heart.ResourceType
import com.artie.heart.StateView

/** A game, declared once for the HEART library. */
object Games {

    val type = ResourceType(
        collection = "games",
        profile = BaseballProfile.profile,
        acts = listOf(
            Act.of("playBall", "play-ball", NoInput::class.java),
            Act.of("pitch", "pitch", Pitch::class.java),
            Act.of("hit", "hit", Hit::class.java),
            Act.of("runnerOut", "runner-out", RunnerOut::class.java),
            Act.of("nextHalf", "next-half", NoInput::class.java),
            Act.of("endGame", "end-game", NoInput::class.java),
        ),
        offers = Offers(
            keys = Phase.entries,
            offered = { phase ->
                when (phase) {
                    Phase.SCHEDULED -> listOf("playBall")
                    Phase.PLAY -> listOf("pitch", "hit", "runnerOut", "endGame")
                    Phase.HALF_OVER -> listOf("nextHalf", "endGame")
                    Phase.GAME_OVER -> listOf("endGame")
                    Phase.FINAL -> emptyList()
                }
            },
            whyAbsent = BaseballProfile::whyAbsent,
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
        val started = game.phase != Phase.SCHEDULED
        return linkedMapOf(
            "id" to game.id,
            "label" to setup.label,
            "phase" to game.phase.name,
            "inning" to game.inning,
            "innings" to Rules.INNINGS,
            "half" to if (game.top) "top" else "bottom",
            "batting" to game.batting(),
            "outs" to game.outs,
            "pitches" to game.pitches,
            "pitchLimit" to Rules.PITCHES,
            "runLimit" to Rules.runLimit(game.inning.coerceAtLeast(1)),
            "runsThisHalf" to game.runsThisHalf,
            "batterUp" to game.batterUp().let { mapOf("number" to it.number, "name" to it.name) },
            "bases" to Bases.names.associateWith { base ->
                game.runner(base)?.let { mapOf("number" to it.number, "name" to it.name) }
            },
            Side.HOME to team(game, Side.HOME, started),
            Side.AWAY to team(game, Side.AWAY, started),
        )
    }

    private fun team(game: GameState, side: String, started: Boolean): Map<String, Any?> {
        val team = checkNotNull(game.setup).team(side)
        val innings = if (started) game.inning else 0
        return linkedMapOf(
            "name" to team.name,
            "runs" to game.score(side),
            "line" to (1..innings).map { game.runsIn(side, it) },
            "lineup" to team.lineup.mapIndexed { index, player ->
                mapOf(
                    "order" to index + 1,
                    "number" to player.number,
                    "name" to player.name,
                    "hits" to game.countFor(side, index, game.hits),
                    "runs" to game.countFor(side, index, game.runs),
                )
            },
        )
    }
}
