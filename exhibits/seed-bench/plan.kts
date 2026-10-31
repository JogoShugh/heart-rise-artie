// A sowing plan, by the account the run is given: a branch that adds trays/<stamp>.yaml to bench-main, and a pull
// request labelled sowing. bench-check fails it when the bench would hold more than 12 trays, so over=true, 60 trays,
// can't be merged.
//   artie run plan.kts owner=JogoShugh crop="Red Sails lettuce" trays=3
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val crop by input(default = "Red Sails lettuce", question = "Which crop?")
val trays by input(default = "3", question = "How many trays?")
val bench by input(default = "2", question = "On which bench?")
val over by input(default = "false", question = "Overfill the bench, 60 trays?")
val label by input(default = "sowing", question = "Which labels mark it, comma-separated?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)

val api = "https://api.github.com/repos/$owner/$repo"
val branch = "sowing/$stamp"
val count = if (over == "true") "60" else trays

bare {
    val base = call("GET", "$api/branches/bench-main")
    call("POST", "$api/git/refs", mapOf("ref" to "refs/heads/$branch", "sha" to base["commit"]["sha"].text))
    call("PUT", "$api/contents/trays/$stamp.yaml", mapOf(
        "message" to "Sow $count trays of $crop on bench $bench",
        "content" to b64("crop: $crop\nbench: $bench\ntrays: $count\n"),
        "branch" to branch,
    ))
    val pull = call("POST", "$api/pulls", mapOf(
        "base" to "bench-main",
        "head" to branch,
        "title" to "Sow $count trays of $crop on bench $bench ($stamp)",
        "body" to "A sowing plan, opened by a stand-in (exhibits/seed-bench).",
    ))
    call("POST", "$api/issues/${pull["number"].text}/labels", mapOf("labels" to label.split(",").map(String::trim)))
}
