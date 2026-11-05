// The GitHub task, reasoned: each move said in words; the model chooses the form or link and its fields from what
// the gateway offers, and ARTIE checks each choice before sending it. The same task as github-task-scripted.kts.
// Needs --reasoner claude. Real writes, to the repository given.
//   artie run tests/github-task-reasoned.kts owner=JogoShugh repo=heart-github-test --reasoner claude
val owner by input()
val repo by input()
val stamp by input(default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")))

heart("http://localhost:8392/") {
    var issue = 0L
    var pull = 0L
    act(intent = "open the repository $owner/$repo") {
        act(intent = "open an issue titled \"Crash when a bed has no name (reasoned)\", saying it was opened by a HEART script") {
            issue = state.long("number")
        }
    }
    act(intent = "open the branch main of $owner/$repo") {
        act(intent = "start a new branch from here, named fix-blank-names-reasoned-$stamp") {
            act(intent = "commit a file named fix-reasoned-$stamp.md, saying \"Bed names may not be blank.\", with the message \"Refuse a bed with no name\"")
            act(intent = "open a pull request into main titled \"Refuse a bed with no name (reasoned)\", saying it relates to #$issue") {
                pull = state.long("number")
                // GitHub works out whether it can merge a new pull request a moment after it opens.
                until(tries = 20) { offers("github:merge") }
                act(intent = "merge it, squashing its commits")
                check("merged" eq true)
            }
        }
    }
    act(intent = "open issue $issue in $owner/$repo") {
        // The comment is a new resource: visit it, then come back to the issue to close it.
        act(intent = "comment on it: Fixed in 1.2 by #$pull.") {
            check("body" eq "Fixed in 1.2 by #$pull.")
        }
        act(intent = "close it, since the fix is done")
        check("state" eq "closed", "state_reason" eq "completed")
    }
}
