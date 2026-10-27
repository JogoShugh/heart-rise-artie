// Made by artie generate --bare from runs/rails-1.yaml and runs/rails-1.upstream.yaml (spec/ideas/bare-runs.md, "Rails"):
// the kept script's watch, its words kept, each act brought down to the call it made to the service, at a
// fixed address. No entry, no forms, no validators, no reasons for absence: where the kept script waits for a
// form, this tries the call, and the service's refusal is the only gate.
import kotlin.time.Duration.Companion.minutes

val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val label by input(default = "dependencies", question = "Which label marks a dependency bump?")
val hold by input(default = "hold", question = "Which label means a person objects?")

bare {
    val watch = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", mapOf("title" to "Merge watch", "body" to "Close this issue to stop the watch."))
    watch(until = { call("GET", "https://api.github.com/repos/${owner}/${repo}/issues/${watch["number"].text}")["state"].text == "closed" }) {
        whenever(call("GET", "https://api.github.com/repos/${owner}/${repo}/issues?labels=${label}")) { item ->
            val member = call("GET", "https://api.github.com/repos/${owner}/${repo}/pulls/${item["number"].text}")
            has(member["labels"] has label)
            leaveWhere("leave it, and say so once.", member["labels"] has hold)
            once("PUT", "https://api.github.com/repos/${owner}/${repo}/pulls/${member["number"].text}/merge", mapOf("merge_method" to "squash", "sha" to member["head"]["sha"].text), giveUp = 3.minutes, otherwise = { why ->
                call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${member["number"].text}/comments", mapOf("body" to write("tell its author in one friendly sentence why it wasn't merged and what to do next. The wait gave up, and the server says why: ${why ?: "no reason given"}")))
            })
        }
    }
}
