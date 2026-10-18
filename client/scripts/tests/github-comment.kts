// Comment on an open issue through the HEART gateway for GitHub, edit the comment, and show that
// reopening an open issue is not offered. Writes only to the repository it is given.
//   artie run tests/github-comment.kts owner=JogoShugh repo=heart-github-test number=10
val owner by input()
val repo by input()
val number by input()
val body by input(default = "Commented by a HEART script.")

heart("http://localhost:8392/") {
    act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to number.toInt())
    check(state["/state"] == "open") { "the issue should be open" }
    expect(NewResource) {
        act("github:comment", "body" to body) {
            act("github:edit", "body" to "$body (edited)")
            check(state["/body"] == "$body (edited)") { "the comment should be edited" }
        }
    }
    expect(NotOffered) { act("github:reopen") }
}
