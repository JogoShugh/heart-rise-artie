// The Dependabot goal (exhibits/goal-syntax/goals/dependabot.goal.md), written by hand in heart-script before the
// goal words Whenever, Until, Once, Otherwise and Give up exist (spec/ideas/goals-that-work-alongside-others.md,
// "Review, 2026-10-23"). Each word is the loop or the branch it will come down to:
//   Until    the watch opens its own issue and stops when someone closes it (or after hours, a safety stop).
//   Whenever the open pull requests, most recently updated first, are read down to the last one seen unchanged;
//            each labelled label is taken up. Events are hints: this is the polling kind, and the gateway asks
//            GitHub with its copy's ETag, so a look at an unchanged pull request costs nothing against the limit.
//   Once     a pull request taken up is merged by squashing as soon as it offers github:merge.
//   Manners  one labelled hold is left alone, and the watch says so once; a refused merge is read again and
//            decided again next time round, never sent again blind.
//   Give up  after giveUp minutes without the merge offered;
//   Otherwise the pull request is told why, in the gateway's own words (hr:unavailable).
//   artie run watch.kts owner=JogoShugh gateway=https://localhost:8392/
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val label by input(default = "dependabot", question = "Which label marks a pull request to merge?")
val hold by input(default = "hold", question = "Which label means a person objects?")
val every by input(default = "15", question = "How many seconds between looks?")
val giveUp by input(default = "10", question = "Give up on a pull request after how many minutes?")
val hours by input(default = "2", question = "Stop the watch after how many hours at most?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)
val gateway by input(default = "http://localhost:8392/", question = "Where is the gateway?")

val repository = arrayOf("owner" to owner, "repo" to repo)
val deadline = java.time.Instant.now().plusSeconds(hours.toLong() * 3600)

/** A remark, kept in the run's record and shown as the run goes (a note alone is not shown). */
fun Here.tell(text: String) {
    note(text)
    println("  note: $text")
}

/** How each comment the watch leaves begins, so a later watch knows what was said. */
val WATCH = "Not merged by the auto-merge watch"

/** Whether the server's reason for not offering an act says it will pass by itself. */
fun passing(why: String?): Boolean = why.orEmpty().contains("read it again")

/** What the watch says, once, of a pull request a person objected to. */
fun leftAlone(number: Long) = "#$number is labelled $hold: left alone until the label goes."

/** The names of the labels the held pull request carries, read from the labels it embeds. */
fun Here.labelled(): Set<String> = embedded("github:labels").mapNotNull { it.read["name"] as? String }.toSet()

heart(gateway) {
    var watch = 0L
    act("github:repos-get", *repository, intent = "get the repository $owner/$repo") {
        act("github:open-issue", intent = "open the issue that keeps the watch going", fields = {
            "title" to "Auto-merge watch ($stamp)"
            "body" to "Pull requests labelled `$label` are merged once they can be; `$hold` keeps one back. " +
                "Close this issue to stop the watch."
        }) {
            watch = state.long("number")
        }
    }

    val waiting = mutableMapOf<Long, java.time.Instant>() // taken up, not yet merged: since when
    val heldBack = mutableSetOf<Long>() // left alone for the hold label, said once
    val finished = mutableSetOf<Long>() // merged, or given up on
    var seen: String? = null // the newest updated_at read so far
    var open = true

    while (open && java.time.Instant.now() < deadline) {
        // A look that fails (GitHub answered 5xx, the network dropped) is noted; the next one tries again.
        try {
            // Whenever: what changed since the last look, newest first, down to the first one older than that.
            act("github:pulls-list", *repository, "state" to "open", "sort" to "updated", "direction" to "desc") {
                var newest = seen
                for (title in links("item").filterNotNull()) {
                    var older = false
                    follow("item", title) {
                        val updated = state.text("updated_at")
                        val number = state.long("number")
                        older = seen != null && updated < seen!!
                        if (!older) {
                            if (newest == null || updated > newest!!) newest = updated
                            val labels = labelled()
                            when {
                                number in finished || label !in labels -> Unit
                                hold in labels -> {
                                    waiting.remove(number)
                                    if (heldBack.add(number)) tell(leftAlone(number))
                                }
                                else -> {
                                    heldBack.remove(number)
                                    waiting.putIfAbsent(number, java.time.Instant.now())
                                }
                            }
                        }
                    }
                    if (older) break
                }
                seen = newest
            }

            // Once each one waiting can be merged, merge it; or give up after giveUp minutes.
            for ((number, since) in waiting.toMap()) {
                try {
                    act("github:pulls-get", *repository, "pull_number" to number) {
                        val waited = java.time.Duration.between(since, java.time.Instant.now())
                        when {
                            state["state"] != "open" -> {
                                tell("#$number was closed or merged by someone else: left as it is.")
                                finished += number
                            }
                            hold in labelled() -> {
                                if (heldBack.add(number)) tell(leftAlone(number))
                                waiting.remove(number)
                            }
                            offers("github:merge") -> {
                                act("github:merge", intent = "merge #$number by squashing", fields = {
                                    "merge_method" to "squash"
                                    "commit_title" to "${state.text("title")} (#$number)"
                                }) {}
                                finished += number
                            }
                            // A reason that says to read again is a passing one (GitHub working mergeability out):
                            // giving up on it would tell the pull request something untrue, so wait one more look.
                            // The words stand in for a mark hr:unavailable doesn't have yet: temporary or not.
                            waited.toMinutes() >= giveUp.toLong() && !passing(why("github:merge")) -> {
                                // Otherwise: say why it wasn't merged, in the gateway's words.
                                val why = why("github:merge") ?: "the merge was never offered"
                                follow("github:issues-get") {
                                    // Said already, by this watch or an earlier one: not said again.
                                    var said = false
                                    follow("github:issues-list-comments") {
                                        said = links("item").filterNotNull()
                                            .any { it.startsWith(WATCH) && it.endsWith(": $why") }
                                    }
                                    if (said) tell("#$number: already told why, by an earlier watch.")
                                    else act("github:comment", intent = "say why #$number wasn't merged", fields = {
                                        "body" to "$WATCH (#$watch) after $giveUp minutes: $why"
                                    }) {}
                                }
                                finished += number
                            }
                        }
                    }
                } catch (stopped: ScriptStopped) {
                    // Refused (someone moved it since it was read): read again and decide again next time round.
                    tell("#$number: ${stopped.reason}; looked at again next time.")
                }
                if (number in finished) waiting.remove(number)
            }

            // Until someone closes the watch's issue.
            act("github:issues-get", *repository, "issue_number" to watch) {
                open = state["state"] == "open"
            }
        } catch (failed: Exception) {
            tell("A look failed: ${failed.message}; looking again next time.")
        }
        if (open) Thread.sleep(every.toLong() * 1000)
    }
}
