// The GitHub task, scripted: every selection written down, no model. From the bookmark: open an issue, branch
// from main, commit the fix, open a pull request that relates to the issue, wait until GitHub can merge it and
// merge it, then comment on the issue that it is fixed in 1.2 and close it. Real writes, to the repository given.
//   artie run tests/github-task-scripted.kts owner=JogoShugh repo=heart-github-test
val owner by input()
val repo by input()
val stamp by input(default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")))

heart("http://localhost:8392/") {
    var issue = 0L
    var pull = 0L
    act("github:repos-get", "owner" to owner, "repo" to repo) {
        act("github:open-issue", "title" to "Crash when a bed has no name (scripted)", "body" to "Opened by a HEART script.") {
            issue = state["/number"] as Long
        }
    }
    // The branch is named, as a person would: a list of branches is paged, and main may not be on its first page.
    act("github:repos-get-branch", "owner" to owner, "repo" to repo, "branch" to "main") {
        act("github:branch-from-here", "name" to "fix-blank-names-scripted-$stamp") {
            act(
                "github:commit-file",
                "path" to "fix-scripted-$stamp.md",
                "message" to "Refuse a bed with no name",
                "content" to "Bed names may not be blank.\n",
            )
            act(
                "github:open-pull-request",
                "title" to "Refuse a bed with no name (scripted)",
                "base" to "main",
                "body" to "Relates to #$issue.",
            ) {
                pull = state["/number"] as Long
                // GitHub works out whether it can merge a new pull request a moment after it opens.
                until(tries = 20) { offers("github:merge") }
                act("github:merge", "merge_method" to "squash")
                check(state["/merged"] == true) { "the pull request should be merged" }
            }
        }
    }
    act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to issue) {
        // The comment is a new resource: visit it, then come back to the issue to close it.
        act("github:comment", "body" to "Fixed in 1.2 by #$pull.") {
            check(state["/body"] == "Fixed in 1.2 by #$pull.") { "the comment should say what fixed it" }
        }
        act("github:close")
        check(state["/state"] == "closed") { "the issue should be closed" }
        check(state["/state_reason"] == "completed") { "the issue should be closed as completed" }
    }
}
