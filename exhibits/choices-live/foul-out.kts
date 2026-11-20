// Choices from state, live (HAL-SCHEMA-FORMS-HR Rule 8). Basketball's record-foul takes its player from the
// chosen team's players still in the game: hr:options at /{team}/players, where fouledOut is false. Game 2 is
// live; Rowan (8, the Hornets, at home) has one foul. The script fouls him out with four more, then asks for a
// fifth foul on him: once by script, which ARTIE refuses before sending, and once in words to Claude, who is
// shown the players as a closed set. Then, in words, a foul on Kai, who is still in. Run on a reset game 2:
//   ../../domains/basketball/reset-db.sh
//   artie run foul-out.kts --reasoner claude --trace runs/foul-out.yaml
val entry by input(default = "http://localhost:8181/")
val rowan = 8

heart(entry) {
    act("basketball:get-game", "id" to 2) {
        repeat(4) { act("basketball:record-foul", "team" to "home", "playerNumber" to rowan, "kind" to "personal") }
        note("Rowan has ${state["home"]}")
        try {
            act("basketball:record-foul", "team" to "home", "playerNumber" to rowan, "kind" to "personal")
        } catch (stopped: ScriptStopped) {
            note("by script: ${stopped.reason}")
        }
        try {
            act(intent = "record a personal foul on Rowan of the Hornets")
        } catch (stopped: ScriptStopped) {
            note("in words, for Rowan: ${stopped.reason}")
        }
        act(intent = "record a personal foul on Kai of the Hornets")
    }
}
