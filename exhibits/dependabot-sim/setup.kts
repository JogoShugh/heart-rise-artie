// The stage for a Dependabot stand-in on the scratch repository (spec/ideas/goals-that-work-alongside-others.md,
// "The workflow to build it on"). Run once; running it again sets everything to the same again.
//   - deps-main: a base branch of its own, so the other exhibits' pull requests into main are untouched;
//     it holds deps.txt, the one "dependency" the bumps change.
//   - The labels: dependabot (the watcher's), dependabot-actions (the rival's) and hold (a person objecting).
// The required check and the rival come in checks.kts, which needs a token that may write workflows.
//   artie run setup.kts owner=JogoShugh
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")

val api = "https://api.github.com/repos/$owner/$repo"

val labels = mapOf(
    "dependabot" to "0366d6",
    "dependabot-actions" to "5319e7",
    "hold" to "b60205",
)

bare {
    // The base branch, from main, unless it is there already.
    if (runCatching { call("GET", "$api/branches/deps-main") }.isFailure) {
        val main = call("GET", "$api/branches/main")
        call("POST", "$api/git/refs", mapOf("ref" to "refs/heads/deps-main", "sha" to main["commit"]["sha"].text))
    }
    val now = runCatching { call("GET", "$api/contents/deps.txt?ref=deps-main") }.getOrNull()
    if (now == null) {
        call("PUT", "$api/contents/deps.txt", mapOf(
            "message" to "Start deps.txt at left-pad 1.0.0",
            "content" to b64("left-pad 1.0.0\n"),
            "branch" to "deps-main",
        ))
    }
    labels.forEach { (name, color) ->
        if (runCatching { call("GET", "$api/labels/$name") }.isFailure) {
            call("POST", "$api/labels", mapOf("name" to name, "color" to color))
        }
    }
}
