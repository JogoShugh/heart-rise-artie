// The triage task, written to keep what a teammate did while it ran (exhibits/native-vs-heart/meddler.py).
// Made from heart-script.kts, the script artie generate made from a run, by hand: each act that a teammate's
// move can take away is made only when the resource still offers it, and the script says what it left, and why.
// It reads no prose to decide: offers(rel) is the manifest, and a draft is the pull request's own state.
//   artie run heart-script-goal.kts owner=JogoShugh stamp=1022-070410 gateway=https://localhost:8392/
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val label by input(default = "bug", question = "Which label marks a bug?")
val stamp by input(default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")))
val gateway by input(default = "http://localhost:8392/", question = "Where is the gateway?")

heart(gateway) {
    var issueNumber = 0L // step 3's /number
    var testNumber = 0L // step 10's /number
    var prBody = "" // step 21's /body
    var prNumber = 0L // step 21's /number
    var testId = 0L // step 10's /id
    var dupeNumber = 0L // step 12's /number
    var issueId = 0L // step 3's /id
    var merged = false

    act("github:repos-get", "owner" to owner, "repo" to repo, intent = "get the repository $owner/$repo") {
        act("github:open-issue", intent = """Open an issue titled "Crash when a bed has no name ($stamp)" with the body: a bed with a blank name crashes the garden""", fields = {
            "title" to "Crash when a bed has no name ($stamp)"
            "body" to "a bed with a blank name crashes the garden"
        }) {
            sync()
            issueNumber = state.long("number")
            issueId = state.long("id")
            act("github:label", "labels" to listOf(label), intent = "Label it with $label") {}
            act("github:assign", "assignees" to listOf(owner), intent = "assign it to $owner") {}
            check("state" to "open")
        }
        act("github:open-issue", intent = """Open an issue titled "Test blank bed names ($stamp)" with the body: part of #${issueNumber}""", fields = {
            "title" to "Test blank bed names ($stamp)"
            "body" to "part of #$issueNumber"
        }) {
            testNumber = state.long("number")
            testId = state.long("id")
        }
        act("github:open-issue", intent = """Open an issue titled "Garden crashes on an empty bed name ($stamp)" with the body: seen on the demo garden""", fields = {
            "title" to "Garden crashes on an empty bed name ($stamp)"
            "body" to "seen on the demo garden"
        }) {
            dupeNumber = state.long("number")
        }
        follow("github:repos-list-branches", intent = "follow the link to list the branches") {
            follow("item", "main", intent = "follow the link to the main branch") {
                act("github:branch-from-here", intent = "start a new branch named fix-blank-names-$stamp from here", fields = {
                    "name" to "fix-blank-names-$stamp"
                }) {
                    sync()
                    act("github:commit-file", intent = """Commit a file named fix-$stamp.md with the content "Bed names may not be blank." and the commit message "Refuse a bed with no name"""", fields = {
                        "path" to "fix-$stamp.md"
                        "message" to "Refuse a bed with no name"
                        "content" to "Bed names may not be blank."
                    }) {}
                    act("github:commit-file", intent = """Commit a file named test-$stamp.md with the content "A bed with a blank name is refused." and the commit message "Test that a blank bed name is refused"""", fields = {
                        "path" to "test-$stamp.md"
                        "message" to "Test that a blank bed name is refused"
                        "content" to "A bed with a blank name is refused."
                    }) {}
                    act("github:open-pull-request", intent = """Open a pull request into main titled "Refuse a bed with no name ($stamp)" with the body: fixes #${issueNumber}""", fields = {
                        "base" to "main"
                        "title" to "Refuse a bed with no name ($stamp)"
                        "body" to "fixes #$issueNumber"
                    }) {
                        sync()
                        prBody = state.text("body")
                        prNumber = state.long("number")
                        // Whatever else the description says now stays: only the line about the fix grows.
                        val said = "fixes #$issueNumber"
                        val tested = "$said and is tested by #$testNumber"
                        // A teammate's edit since it was read refuses the act as stale: read it again, decide again.
                        decide {
                            val now = state.text("body")
                            act("github:edit", intent = "change its description to: fixes #${issueNumber} and is tested by #${testNumber}", fields = {
                                "body" to if (said in now) now.replace(said, tested) else "$now\n\n$tested"
                            }) {}
                        }
                        // GitHub works mergeability out a moment after; a draft the team holds back is never offered.
                        until(tries = 20) { offers("github:merge") || state["draft"] == true }
                        if (offers("github:merge")) {
                            act("github:merge", intent = """merge it by squashing its commits, with the commit title "Refuse a bed with no name (#${prNumber})"""", fields = {
                                "merge_method" to "squash"
                                "commit_title" to "Refuse a bed with no name (#$prNumber)"
                            }) {}
                            check("merged" to true)
                            merged = true
                        } else {
                            note("pull request #$prNumber left unmerged: ${why("github:merge")}")
                        }
                    }
                }
            }
        }
    }
    act("github:issues-get", intent = "get issue ${issueNumber} of $owner/$repo", fields = {
        "owner" to owner
        "repo" to repo
        "issue_number" to issueNumber
    }) {
        act("github:add-sub-issue", "sub_issue_id" to testId, intent = "add sub-issue by id ${testId}") {}
        val news = if (merged) "Fixed in #$prNumber, tested in #$testNumber." else "Waiting on #$prNumber, held as a draft."
        act("github:comment", intent = "comment on it: Fixed in #${prNumber}, tested in #${testNumber}.", fields = {
            "body" to news
        }) {}
        decide {
            if (merged && offers("github:lock")) {
                act("github:lock", "lock_reason" to "resolved", intent = "lock the conversation as resolved") {}
            } else {
                note("issue #$issueNumber left unlocked or as locked: ${why("github:lock") ?: "not merged"}")
            }
        }
    }
    act("github:issues-get", intent = "get issue ${testNumber} of $owner/$repo", fields = {
        "owner" to owner
        "repo" to repo
        "issue_number" to testNumber
    }) {
        decide {
            if (merged && offers("github:close")) {
                act("github:comment", intent = "comment on it: The test went in with #${prNumber}.", fields = {
                    "body" to "The test went in with #$prNumber."
                }) {}
                act("github:close", intent = "close it as completed") {}
                check("state" to "closed")
            } else {
                note("issue #$testNumber left as it is: ${why("github:close") ?: "not merged"}")
            }
        }
    }
    act("github:issues-get", intent = "get issue ${dupeNumber} of $owner/$repo", fields = {
        "owner" to owner
        "repo" to repo
        "issue_number" to dupeNumber
    }) {
        // Closed first, then said: a close refused for a teammate's close leaves nothing said that isn't so.
        var closed = false
        decide {
            if (offers("github:close-duplicate")) {
                act("github:close-duplicate", intent = "close it as a duplicate of the issue with id ${issueId}", fields = {
                    "duplicate_issue_id" to issueId
                }) {}
                closed = true
            } else {
                note("issue #$dupeNumber left as the team closed it: ${why("github:close-duplicate")}")
            }
        }
        if (closed) {
            act("github:comment", intent = "comment on it: Same crash as #${issueNumber}.", fields = {
                "body" to "Same crash as #$issueNumber."
            }) {}
            check("state" to "closed", "state_reason" to "duplicate")
        }
    }
}
