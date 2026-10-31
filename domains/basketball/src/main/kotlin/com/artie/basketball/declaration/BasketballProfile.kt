package com.artie.basketball.declaration

import com.artie.basketball.domain.Phase
import com.artie.heart.AlpsType
import com.artie.heart.Profile
import com.artie.heart.Relation

/** The basketball vocabulary (served as ALPS at `/profiles/basketball`), and why each act is absent in each phase. */
object BasketballProfile {

    val profile = Profile(
        name = "basketball",
        title = "Youth basketball scoring",
        doc = "What a scorer records during a youth basketball game. Only a game's current manifest " +
            "says what may be recorded now.",
        relations = listOf(
            act("tip-off", "Tip off", "Starts the game; the team that won the tip has the ball."),
            act("score-basket", "Score a basket", "Two or three points for a player; the ball goes to the other team."),
            act("free-throw", "Free throw", "One point for a player when it goes in."),
            act("record-foul", "Record a foul", "A foul by a player. Five and the player is out."),
            act("turnover", "Turnover", "The team with the ball loses it to the other team."),
            act("call-timeout", "Call a timeout", "Stops play until play resumes."),
            act("resume-play", "Resume play", "Ends a timeout."),
            act("end-period", "End the period", "Ends the period being played."),
            act("start-period", "Start the next period", "Starts the next period after a break."),
            act("final-whistle", "Final whistle", "Ends the game once every period is played."),
            act("correct-score", "Take back a scoring play", "Removes a basket or free throw recorded by mistake."),
            Relation("search-games", AlpsType.SAFE, "Find games by name", "Lists the games whose name has the text.",
                "A safe search; each result links to the game, titled with its name."),
            Relation("get-game", AlpsType.SAFE, "Get a game by its id", "Goes to one game, given its id.",
                "A safe lookup; the result is the game, with what may be recorded now."),
        ),
    )

    private fun act(id: String, title: String, description: String) =
        Relation(id, AlpsType.UNSAFE, title, description, description)

    /** Why act [name] is absent in [phase]. A function of the phase alone. */
    fun whyAbsent(phase: Phase, name: String): String =
        when (phase) {
            Phase.SCHEDULED -> "The game has not started; tip off first."
            Phase.LIVE -> when (name) {
                "tipOff" -> "The game has already started."
                "resumePlay" -> "Play is not stopped."
                else -> "A period is being played; end it first."
            }
            Phase.TIMEOUT -> when (name) {
                "tipOff" -> "The game has already started."
                else -> "Play is stopped for a timeout; resume play first."
            }
            Phase.BREAK -> "The period is over; start the next period first."
            Phase.FULL_TIME -> "Every period is played; blow the final whistle."
            Phase.FINAL -> "The game is over."
        }
}
