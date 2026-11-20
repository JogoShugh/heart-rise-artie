// The same task as a HEART script, no model: the title worked out from the issue as it is (decide { } again
// after a stale refusal), and closed only if closing is offered now; otherwise the gateway's reason is noted.
val gateway by input(default = "http://localhost:8098")

heart("$gateway/repos/acme/web/issues/7") {
    decide {
        act("github:edit", "title" to "${state["title"]} (triaged)")
    }
    if (offers("github:close")) act("github:close") else note("not closed: ${why("github:close")}")
}
