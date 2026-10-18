// The GitHub task, reasoned: each move said in words; the model chooses the form and its fields from what
// the gateway offers, and ARTIE checks each choice before sending it. Needs --reasoner claude.
// The pull request comes from github-task-setup.sh.
//   artie run tests/github-task-reasoned.kts owner=JogoShugh repo=heart-github-test pull=12 --reasoner claude
val owner by input()
val repo by input()
val pull by input()

heart("http://localhost:8392/") {
    var issue = 0L
    reason("open the repository $owner/$repo") {
        reason("open an issue titled \"Crash when a bed has no name (reasoned)\", saying it was opened by a HEART script") {
            issue = state["/number"] as Long
        }
    }
    reason("open pull request $pull in $owner/$repo") {
        reason("change its description to say it refuses a bed with no name and relates to #$issue")
        reason("merge it, squashing its commits")
        check(state["/merged"] == true) { "the pull request should be merged" }
    }
    reason("open issue $issue in $owner/$repo") {
        reason("comment on it: Fixed in 1.2 by #$pull.")
        reason("close it, since the fix is done")
        check(state["/state"] == "closed") { "the issue should be closed" }
        check(state["/state_reason"] == "completed") { "the issue should be closed as completed" }
    }
}
