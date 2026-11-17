package com.artie.baseball.declaration

import com.artie.baseball.domain.Phase
import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation

/** The baseball vocabulary (served as ALPS at `/profiles/baseball`), and why each act is absent in each phase. */
object BaseballProfile {

    val profile = Profile(
        name = "baseball",
        title = "Youth baseball scoring, 8U coach pitch",
        doc = "What a scorekeeper records during an 8U coach-pitch baseball game. Only a game's current manifest " +
            "says what may be recorded now.",
        relations = listOf(
            act("play-ball", "Play ball", "Starts the game; the visitors bat first."),
            act("pitch", "Pitch", "A pitch not hit into play: missed, or fouled off. The sixth miss is out."),
            act("hit", "Ball in play", "The batter hits: a single, double, triple or home run, or is fielded out."),
            act("runner-out", "Runner out", "A runner put out on a base."),
            act("next-half", "Next half-inning", "The other team comes up to bat."),
            act("end-game", "End the game", "Ends the game: every inning played, or the umpire calls time."),
            Relation(
                "search-games",
                AlpsType.SAFE,
                "Find games by name",
                "Lists the games whose name has the text.",
                "A safe search; each result links to the game, titled with its name.",
            ),
            Relation(
                "get-game",
                AlpsType.SAFE,
                "Get a game by its id",
                "Goes to one game, given its id.",
                "A safe lookup; the result is the game, with what may be recorded now.",
            ),
        ),
    )

    private fun act(id: String, title: String, description: String) =
        Relation(id, AlpsType.UNSAFE, title, description, description)

    /** Why act [name] is absent in [phase]. A function of the phase alone. */
    fun whyAbsent(phase: Phase, name: String): String =
        when {
            phase == Phase.SCHEDULED -> "The game has not started; play ball first."
            phase == Phase.FINAL -> "The game is over."
            name == "playBall" -> "The game has already started."
            phase == Phase.PLAY -> "The half-inning is still being played."
            phase == Phase.HALF_OVER -> "The half-inning is over; start the next half first."
            else -> "Every inning is played; end the game."
        }
}
