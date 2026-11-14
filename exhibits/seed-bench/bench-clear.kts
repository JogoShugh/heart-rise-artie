// Clears the seed bench (spec/PLAN-SEED-BENCH.md) of every sowing plan merged into bench-main, leaving the two trays
// bench-setup.kts sowed, so plans fit again under bench-check's 12 trays. Lifts bench-main's protection to commit,
// and sets it again as bench-setup.kts does.
//   artie run bench-clear.kts owner=JogoShugh
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")

val api = "https://api.github.com/repos/$owner/$repo"

bare {
    runCatching { call("DELETE", "$api/branches/bench-main/protection") }
    val trays = call("GET", "$api/contents/trays?ref=bench-main") as kotlinx.serialization.json.JsonArray
    trays.filter { it["name"].text != "start.yaml" }.forEach { tray ->
        call("DELETE", "$api/contents/${tray["path"].text}", mapOf(
            "message" to "Clear ${tray["name"].text} from the bench",
            "sha" to tray["sha"].text,
            "branch" to "bench-main",
        ))
    }
    call("PUT", "$api/branches/bench-main/protection", mapOf(
        "required_status_checks" to mapOf("strict" to false, "contexts" to listOf("bench-check")),
        "enforce_admins" to true,
        "required_pull_request_reviews" to null,
        "restrictions" to null,
    ))
}
