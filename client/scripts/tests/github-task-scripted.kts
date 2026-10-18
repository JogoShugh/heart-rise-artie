// The GitHub task, scripted: every selection written down. Open an issue in the scratch repository, point
// a ready pull request at it and merge the pull request, then comment on the issue that it is fixed in 1.2
// and close it as completed. No model. The pull request comes from github-task-setup.sh.
//   artie run tests/github-task-scripted.kts owner=JogoShugh repo=heart-github-test pull=12
val owner by input()
val repo by input()
val pull by input()
val title by input(default = "Crash when a bed has no name (scripted)")

heart("http://localhost:8392/") {
    var issue = 0L
    act("github:repos-get", "owner" to owner, "repo" to repo) {
        act("github:open-issue", "title" to title, "body" to "Opened by a HEART script.") {
            issue = state["/number"] as Long
        }
    }
    act("github:pulls-get", "owner" to owner, "repo" to repo, "pull_number" to pull.toInt()) {
        act("github:edit", "body" to "Refuses a bed with no name. Relates to #$issue.")
        act("github:merge", "merge_method" to "squash")
        check(state["/merged"] == true) { "the pull request should be merged" }
    }
    act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to issue) {
        act("github:comment", "body" to "Fixed in 1.2 by #$pull.")
        act("github:close")
        check(state["/state"] == "closed") { "the issue should be closed" }
        check(state["/state_reason"] == "completed") { "the issue should be closed as completed" }
    }
}
