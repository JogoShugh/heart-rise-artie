// A live watch with three handlers (spec/PLAN-LISTENERS.md, slice 9), as listeners, each on its own, or as today's
// single loop (mode=loop), the same handlers in turn at each look:
// - veracode: when a stand-in veracode-bot says a scan FAILED on the scan issue, label it security-hold and say so,
//   once;
// - alerts: answer each issue labelled live-alert once;
// - triage: handle each new comment on the triage issue slowly, as a model call is slow ([slow] seconds).
// It ends when the watch's issue is closed. Conditions are facts blocks and guards
// (spec/ideas/dsl-conditions-as-facts.md); the script has no if of its own.
//   artie run watch.kts owner=JogoShugh watch=801 scan=802 triage=803 mode=listeners --rung L2
import kotlin.time.Duration.Companion.seconds

val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val watch by input(question = "The watch's issue?")
val scan by input(question = "The scan issue?")
val triage by input(question = "The triage issue?")
val mode by input(default = "listeners", question = "Listeners, or one loop?")
val every by input(default = "10", question = "How often, in seconds?")
val slow by input(default = "15", question = "How slow a triage is, in seconds?")
val gateway by input(default = "https://localhost:8392/", question = "Where is the gateway?")

val pace = every.toInt().seconds

/** The scan issue: a failed scan the bot reports holds it for security, said once. */
fun Here.scans() = act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to scan.toInt()) {
    val issue = held.address
    follow("github:issues-list-comments") {
        whenever {
            saying("Veracode scan FAILED")
            at(issue) {
                given { "github:labels" has "security-hold" } otherwise {
                    act("github:label", "labels" to listOf("security-hold")) {}
                    act("github:comment", "body" to "Held for security until the scan passes.") {}
                    note("held #$scan at ${java.time.Instant.now()}")
                }
            }
        }
    }
}

/** Each live alert, answered once. */
fun Here.alerts() = act(
    "github:issues-list-for-repo", "owner" to owner, "repo" to repo, "state" to "open", "labels" to "live-alert",
) {
    whenever {
        actOnce("github:comment", "body" to "Seen by the watch.", listedIn = "github:issues-list-comments") {
            note("answered an alert at ${java.time.Instant.now()}")
        }
    }
}

/** Each new comment on the triage issue, handled slowly. */
fun Here.triaged() = act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to triage.toInt()) {
    follow("github:issues-list-comments") {
        whenever {
            Thread.sleep(slow.toLong() * 1000)
            note("triaged a comment at ${java.time.Instant.now()}")
        }
    }
}

heart(gateway) {
    act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to watch.toInt()) { keepAs("watch") }
    given { that(mode == "loop") } then {
        watch(every = pace, until = { at("watch") { "state" eq "closed" } }) {
            scans()
            alerts()
            triaged()
        }
    } otherwise {
        listeners(every = pace, until = { at("watch") { "state" eq "closed" } }) {
            listen("veracode", every = pace) { scans() }
            listen("alerts", every = pace) { alerts() }
            listen("triage", every = pace) { triaged() }
        }
    }
}
