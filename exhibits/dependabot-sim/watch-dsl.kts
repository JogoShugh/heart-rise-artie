// The Dependabot goal (exhibits/goal-syntax/goals/dependabot-sim.goal.md) in heart-script's own watch words:
// watch (Until), whenever (Whenever), once (Once, Or give up, Otherwise). watch.kts wrote the same by hand, with its
// own loop, maps and clock; here heart-script keeps them.
//   artie run watch-dsl.kts owner=JogoShugh gateway=https://localhost:8392/ --rung L2
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val label by input(default = "dependabot", question = "Which label marks a pull request to merge?")
val hold by input(default = "hold", question = "Which label means a person objects?")
val every by input(default = "15", question = "How many seconds between looks?")
val giveUp by input(default = "10", question = "Give up on a pull request after how many minutes?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)
val gateway by input(default = "http://localhost:8392/", question = "Where is the gateway?")

val repository = arrayOf("owner" to owner, "repo" to repo)

/** How each comment the watch leaves begins, so a later watch knows what was said. */
val WATCH = "Not merged by the auto-merge watch"

/** The names of the labels the held pull request carries. */
fun Here.labelled(): Set<String> = embedded("github:labels").mapNotNull { it.read["name"] as? String }.toSet()

heart(gateway) {
    var watch = 0L
    act("github:repos-get", *repository) {
        act("github:open-issue", fields = {
            "title" to "Auto-merge watch ($stamp)"
            "body" to "Pull requests labelled `$label` are merged once they can be; `$hold` keeps one back. " +
                "Close this issue to stop the watch."
        }) { watch = state.long("number") }
    }
    // The watch stands at the entry, where both the list and the watch's issue are reached from.
    // Until someone closes the watch's issue.
    watch(every = every.toInt().seconds, until = {
        var closed = false
        act("github:issues-get", *repository, "issue_number" to watch) { closed = state["state"] != "open" }
        closed
    }) {
        act("github:pulls-list", *repository, "state" to "open") {
            // Whenever a pull request labelled `label` changes, unless a person holds it back.
            whenever {
                val labels = labelled()
                if (label in labels && hold !in labels) {
                    val number = state.long("number")
                    once("github:merge", giveUp = giveUp.toInt().minutes, otherwise = { why ->
                        // Otherwise, say why, unless a watch said it already.
                        val said = "$WATCH (#$watch) after $giveUp minutes: ${why ?: "the merge was never offered"}"
                        follow("github:issues-get") {
                            var already = false
                            follow("github:issues-list-comments") {
                                already = links("item").filterNotNull().any { it.startsWith(WATCH) && it.endsWith(why.orEmpty()) }
                            }
                            if (!already) act("github:comment", "body" to said) {}
                        }
                    }) {
                        act("github:merge", "merge_method" to "squash", "commit_title" to "${state.text("title")} (#$number)") {}
                    }
                }
            }
        }
    }
}
