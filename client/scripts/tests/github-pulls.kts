// Read pull requests through the HEART gateway for GitHub and show what each one's state offers:
// a ready one can be merged; a draft can't, and the gateway says why. Reads only: nothing is merged.
//   artie run tests/github-pulls.kts owner=JogoShugh repo=heart-github-test ready=1 draft=2
val owner by input()
val repo by input()
val ready by input(default = "1")
val draft by input(default = "2")

heart("http://localhost:8392/") {
    act("github:pulls-get", "owner" to owner, "repo" to repo, "pull_number" to ready.toInt()) {
        check(offers("github:merge")) { "a ready pull request should offer merge" }
        note("#$ready offers ${held.offered}")
    }
    act("github:pulls-get", "owner" to owner, "repo" to repo, "pull_number" to draft.toInt()) {
        check(state["/draft"] == true) { "#$draft should be a draft" }
        expect(NotOffered) { act("github:merge") }
        note("#$draft: merge not offered: ${why("github:merge")}")
    }
}
