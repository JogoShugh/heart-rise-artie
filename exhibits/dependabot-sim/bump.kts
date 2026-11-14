// A Dependabot stand-in, by the account the run is given: a branch that bumps left-pad in deps.txt, a pull request
// into deps-main titled the way Dependabot titles one, and the label that marks it (setup.kts made the stage).
// label=dependabot is for the watcher (watch.kts); label=dependabot,hold opens it already objected to;
// label=dependabot-actions is for the rival (checks.kts).
// draft=true opens it as a draft, which can't be merged until someone marks it ready.
//   artie run bump.kts owner=JogoShugh to=1.0.1
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val from by input(default = "1.0.0", question = "Bump from which version?")
val to by input(question = "Bump to which version?")
val label by input(default = "dependabot", question = "Which labels mark it, comma-separated?")
val draft by input(default = "false", question = "Open it as a draft?")
// Bumps that change one file conflict once one of them merges; give each its own file to keep them apart.
val file by input(default = "deps.txt", question = "Which file holds the version?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)

val api = "https://api.github.com/repos/$owner/$repo"
val branch = "deps/left-pad-$to-$stamp"

bare {
    val base = call("GET", "$api/branches/deps-main")
    call("POST", "$api/git/refs", mapOf("ref" to "refs/heads/$branch", "sha" to base["commit"]["sha"].text))
    val now = runCatching { call("GET", "$api/contents/$file?ref=$branch") }.getOrNull()
    call("PUT", "$api/contents/$file", mapOf(
        "message" to "Bump left-pad from $from to $to",
        "content" to b64("left-pad $to\n"),
        "branch" to branch,
    ) + (now?.let { mapOf("sha" to it["sha"].text) } ?: emptyMap()))
    val pull = call("POST", "$api/pulls", mapOf(
        "base" to "deps-main",
        "head" to branch,
        "title" to "Bump left-pad from $from to $to ($stamp)",
        "body" to "Bumps left-pad from $from to $to. Opened by a Dependabot stand-in (exhibits/dependabot-sim).",
        "draft" to (draft == "true"),
    ))
    call("POST", "$api/issues/${pull["number"].text}/labels", mapOf("labels" to label.split(",").map(String::trim)))
}
