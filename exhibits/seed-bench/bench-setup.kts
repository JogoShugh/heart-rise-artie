// The stage for the seed bench (spec/PLAN-SEED-BENCH.md) on the scratch repository. Run once; running it again sets
// everything to the same again, and opens two more trays.
//   - bench-main: a base branch of its own, holding trays/, one file per sowing plan merged.
//   - bench-check: a required Actions check on every plan into bench-main; it fails when the bench would hold more
//     than 12 trays. The token must be allowed to write workflows.
//   - The labels: tray and its stages, alert, sowing, and hold (a person objecting).
//   - Two trays already sown: issues labelled tray and sown.
//   artie run bench-setup.kts owner=JogoShugh
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")

val api = "https://api.github.com/repos/$owner/$repo"

val labels = mapOf(
    "tray" to "0e8a16",
    "sown" to "c2e0c6",
    "germinated" to "bfdadc",
    "transplanted" to "5319e7",
    "alert" to "d93f0b",
    "sowing" to "1d76db",
    "hold" to "b60205",
)

val check = """
    name: bench-check
    on:
      pull_request:
        branches: [bench-main]
    jobs:
      bench-check:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - name: The bench holds 12 trays at most
            run: |
              total=${'$'}(cat trays/*.yaml 2>/dev/null | awk '/^trays:/ { s += ${'$'}2 } END { print s + 0 }')
              echo "Trays on the bench: ${'$'}total of 12"
              [ "${'$'}total" -le 12 ]
""".trimIndent() + "\n"

bare {
    if (runCatching { call("GET", "$api/branches/bench-main") }.isFailure) {
        val main = call("GET", "$api/branches/main")
        call("POST", "$api/git/refs", mapOf("ref" to "refs/heads/bench-main", "sha" to main["commit"]["sha"].text))
    }
    // A protected bench-main takes no direct commits, so a second run lifts the protection first and sets it again.
    runCatching { call("DELETE", "$api/branches/bench-main/protection") }
    fun put(path: String, content: String, message: String) {
        val now = runCatching { call("GET", "$api/contents/$path?ref=bench-main") }.getOrNull()
        val body = mapOf("message" to message, "content" to b64(content), "branch" to "bench-main") +
            (now?.let { mapOf("sha" to it["sha"].text) } ?: emptyMap())
        call("PUT", "$api/contents/$path", body)
    }
    put("trays/start.yaml", "crop: Red Sails lettuce\nbench: 2\ntrays: 2\n", "Two trays already on the bench")
    put(".github/workflows/bench-check.yml", check, "The check every sowing plan into bench-main must pass")
    call("PUT", "$api/branches/bench-main/protection", mapOf(
        "required_status_checks" to mapOf("strict" to false, "contexts" to listOf("bench-check")),
        "enforce_admins" to true,
        "required_pull_request_reviews" to null,
        "restrictions" to null,
    ))
    labels.forEach { (name, color) ->
        if (runCatching { call("GET", "$api/labels/$name") }.isFailure) {
            call("POST", "$api/labels", mapOf("name" to name, "color" to color))
        }
    }
    listOf("Tray 14: Red Sails lettuce, 128 cells, bench 2", "Tray 15: Bright Lights chard, 128 cells, bench 2")
        .forEach { title ->
            call("POST", "$api/issues", mapOf("title" to title, "body" to "Sown. A seed bench tray.",
                "labels" to listOf("tray", "sown")))
        }
}
