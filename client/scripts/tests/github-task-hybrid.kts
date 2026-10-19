// The GitHub task, hybrid: the script does every step exactly, as github-task-scripted.kts does, but the one
// choice that is a matter of judgement, which way to close the issue (completed, not planned, or a duplicate),
// the model makes. Needs --reasoner claude. Real writes, to the repository given.
//   artie run tests/github-task-hybrid.kts owner=JogoShugh repo=heart-github-test --reasoner claude
val owner by input()
val repo by input()
val stamp by input(default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")))

heart("http://localhost:8392/") {
    var issue = 0L
    var pull = 0L
    act("github:repos-get", "owner" to owner, "repo" to repo) {
        act("github:open-issue", "title" to "Crash when a bed has no name (hybrid)", "body" to "Opened by a HEART script.") {
            issue = state["number"] as Long
        }
    }
    // The branch is named, as a person would: a list of branches is paged, and main may not be on its first page.
    act("github:repos-get-branch", "owner" to owner, "repo" to repo, "branch" to "main") {
        act("github:branch-from-here", "name" to "fix-blank-names-hybrid-$stamp") {
            act(
                "github:commit-file",
                "path" to "fix-hybrid-$stamp.md",
                "message" to "Refuse a bed with no name",
                "content" to "Bed names may not be blank.\n",
            )
            act(
                "github:open-pull-request",
                "title" to "Refuse a bed with no name (hybrid)",
                "base" to "main",
                "body" to "Relates to #$issue.",
            ) {
                pull = state["number"] as Long
                // GitHub works out whether it can merge a new pull request a moment after it opens.
                until(tries = 20) { offers("github:merge") }
                act("github:merge", "merge_method" to "squash")
                check("merged" eq true)
            }
        }
    }
    act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to issue) {
        // The comment is a new resource: visit it, then come back to the issue to close it.
        act("github:comment", "body" to "Fixed in 1.2 by #$pull.") {
            check("body" eq "Fixed in 1.2 by #$pull.")
        }
        act(intent = "close it the right way: the fix shipped in 1.2")
        check("state" eq "closed", "state_reason" eq "completed")
    }
}
