// Made by artie generate --bare from careful-heart-1022-205149.yaml and /private/tmp/claude-501/-Users-josh-repos-artie/ce35ac87-5f2b-400b-873a-eb705d98bae2/scratchpad/upstream.record.yaml (spec/ideas/bare-runs.md): the calls the
// run came down to, sent straight to the service, with no entry, no forms, no validators and no checks.
val gateway by input(default = "http://localhost:8392/", question = "Where is the gateway?")
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val stamp by input(default = "1022-205151")
val label by input(default = "bug", question = "Which label marks a bug?")

bare {
    val r4 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", mapOf("title" to "Crash when a bed has no name (${stamp})", "body" to "a bed with a blank name crashes the garden"))
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r4["number"].text}/labels", mapOf("labels" to listOf("${label}")))
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r4["number"].text}/assignees", mapOf("assignees" to listOf("${owner}")))
    val r41 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", mapOf("title" to "Test blank bed names (${stamp})", "body" to "part of #${r4["number"].text}"))
    val r49 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", mapOf("title" to "Garden crashes on an empty bed name (${stamp})", "body" to "seen on the demo garden"))
    val r79 = call("GET", "https://api.github.com/repos/${owner}/${repo}/branches/main")
    call("POST", "https://api.github.com/repos/${owner}/${repo}/git/refs", mapOf("ref" to "refs/heads/fix-blank-names-${stamp}", "sha" to r79["commit"]["sha"].text))
    call("PUT", "https://api.github.com/repos/${owner}/${repo}/contents/fix-${stamp}.md", mapOf("message" to "Refuse a bed with no name", "content" to "QmVkIG5hbWVzIG1heSBub3QgYmUgYmxhbmsu", "branch" to "fix-blank-names-${stamp}"))
    val r93 = call("PUT", "https://api.github.com/repos/${owner}/${repo}/contents/test-${stamp}.md", mapOf("message" to "Test that a blank bed name is refused", "content" to "QSBiZWQgd2l0aCBhIGJsYW5rIG5hbWUgaXMgcmVmdXNlZC4=", "branch" to "fix-blank-names-${stamp}"))
    val r98 = call("POST", "https://api.github.com/repos/${owner}/${repo}/pulls", mapOf("base" to "main", "title" to "Refuse a bed with no name (${stamp})", "body" to "fixes #${r4["number"].text}", "head" to "fix-blank-names-${stamp}"))
    call("PATCH", "https://api.github.com/repos/${owner}/${repo}/pulls/${r98["number"].text}", mapOf("body" to "fixes #${r4["number"].text} and is tested by #${r41["number"].text}"))
    call("PUT", "https://api.github.com/repos/${owner}/${repo}/pulls/${r98["number"].text}/merge", mapOf("merge_method" to "squash", "commit_title" to "Refuse a bed with no name (#${r98["number"].text})", "sha" to r93["commit"]["sha"].text), retry = 30)
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r4["number"].text}/sub_issues", mapOf("sub_issue_id" to r41["id"]))
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r4["number"].text}/comments", mapOf("body" to "Fixed in #${r98["number"].text}, tested in #${r41["number"].text}."))
    call("PUT", "https://api.github.com/repos/${owner}/${repo}/issues/${r4["number"].text}/lock", mapOf("lock_reason" to "resolved"))
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r41["number"].text}/comments", mapOf("body" to "The test went in with #${r98["number"].text}."))
    call("PATCH", "https://api.github.com/repos/${owner}/${repo}/issues/${r41["number"].text}", mapOf("state" to "closed", "state_reason" to "completed"))
    call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r49["number"].text}/comments", mapOf("body" to "Same crash as #${r4["number"].text}."))
    call("PATCH", "https://api.github.com/repos/${owner}/${repo}/issues/${r49["number"].text}", mapOf("state" to "closed", "state_reason" to "duplicate", "duplicate_issue_id" to r4["id"]))
}
