// A security scanner stand-in (a veracode-bot, say): opens an issue for a failed scan, labelled for the watch
// (dependabot-scan.goal.md) to answer.
//   artie run finding.kts owner=JogoShugh what="SQL injection in the bed search"
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val what by input(question = "What did the scan find?")
val label by input(default = "scan", question = "Which label marks a failed scan?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
    question = "Which stamp tells this run's issues apart?",
)

bare {
    call(
        "POST",
        "https://api.github.com/repos/$owner/$repo/issues",
        mapOf("title" to "Scan failed: $what ($stamp)", "body" to "Found by the scan stand-in.", "labels" to listOf(label)),
    )
}
