// Made by bare.kts from ARTIE run af5d642efaf69a892ba627659a930276: 19 of its 176 GitHub calls, made directly.
// No gateway, no forms, no schema, no checks. Inputs as name=value; the GitHub token from GITHUB_TOKEN.
//   artie run bare-script.kts repo=heart-github-test stamp=1022-130257 owner=JogoShugh label=bug
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlinx.serialization.json.*

val repo by input(default = "heart-github-test")
val stamp by input(default = "1022-130257")
val owner by input(default = "JogoShugh")
val label by input(default = "bug")
val token = System.getenv("GITHUB_TOKEN") ?: error("GITHUB_TOKEN is not set")
val http = HttpClient.newHttpClient()

fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
fun json(v: Any?): JsonElement = when (v) {
    is JsonElement -> v
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to json(x) })
    is List<*> -> JsonArray(v.map(::json))
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    else -> JsonNull
}
fun obj(vararg members: Pair<String, Any?>) = members.toMap()
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.get(index)
val JsonElement?.text: String get() = (this as JsonPrimitive).content

fun call(method: String, url: String, body: Any? = null, retry: Boolean = false): JsonElement? {
    println("$method $url")
    var failed = ""
    repeat(if (retry) 30 else 1) {
        val sent = if (body == null) HttpRequest.BodyPublishers.noBody()
        else HttpRequest.BodyPublishers.ofString(json(body).toString())
        val answer = http.send(HttpRequest.newBuilder(URI(url)).method(method, sent)
            .header("Authorization", "Bearer $token").header("Accept", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("Content-Type", "application/json").build(),
            HttpResponse.BodyHandlers.ofString())
        if (answer.statusCode() in 200..299) return answer.body().takeIf { it.isNotBlank() }?.let(Json::parseToJsonElement)
        failed = "$method $url: HTTP ${answer.statusCode()} ${answer.body().take(300)}"
        Thread.sleep(1000)
    }
    error(failed)
}

val r3 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", obj("title" to "Crash when a bed has no name (${stamp})", "body" to "a bed with a blank name crashes the garden"))
val r14 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r3["number"].text}/labels", obj("labels" to listOf("${label}")))
val r24 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r3["number"].text}/assignees", obj("assignees" to listOf("${owner}")))
val r30 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", obj("title" to "Test blank bed names (${stamp})", "body" to "part of #${r3["number"].text}"))
val r37 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues", obj("title" to "Garden crashes on an empty bed name (${stamp})", "body" to "seen on the demo garden"))
val r54 = call("GET", "https://api.github.com/repos/${owner}/${repo}/branches/main")
val r57 = call("POST", "https://api.github.com/repos/${owner}/${repo}/git/refs", obj("ref" to "refs/heads/fix-blank-names-${stamp}", "sha" to r54["commit"]["sha"].text))
val r62 = call("PUT", "https://api.github.com/repos/${owner}/${repo}/contents/fix-${stamp}.md", obj("message" to "Refuse a bed with no name", "content" to "QmVkIG5hbWVzIG1heSBub3QgYmUgYmxhbmsu", "branch" to "fix-blank-names-${stamp}"))
val r66 = call("PUT", "https://api.github.com/repos/${owner}/${repo}/contents/test-${stamp}.md", obj("message" to "Test that a blank bed name is refused", "content" to "QSBiZWQgd2l0aCBhIGJsYW5rIG5hbWUgaXMgcmVmdXNlZC4=", "branch" to "fix-blank-names-${stamp}"))
val r70 = call("POST", "https://api.github.com/repos/${owner}/${repo}/pulls", obj("base" to "main", "title" to "Refuse a bed with no name (${stamp})", "body" to "fixes #${r3["number"].text}", "head" to "fix-blank-names-${stamp}"))
val r78 = call("PATCH", "https://api.github.com/repos/${owner}/${repo}/pulls/${r70["number"].text}", obj("body" to "fixes #${r3["number"].text} and is tested by #${r30["number"].text}"))
val r85 = call("PUT", "https://api.github.com/repos/${owner}/${repo}/pulls/${r70["number"].text}/merge", obj("merge_method" to "squash", "commit_title" to "Refuse a bed with no name (#${r70["number"].text})", "sha" to r66["commit"]["sha"].text), retry = true)
val r97 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r3["number"].text}/sub_issues", obj("sub_issue_id" to r30["id"]))
val r107 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r3["number"].text}/comments", obj("body" to "Fixed in #${r70["number"].text}, tested in #${r30["number"].text}."))
val r120 = call("PUT", "https://api.github.com/repos/${owner}/${repo}/issues/${r3["number"].text}/lock", obj("lock_reason" to "resolved"))
val r133 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r30["number"].text}/comments", obj("body" to "The test went in with #${r70["number"].text}."))
val r146 = call("PATCH", "https://api.github.com/repos/${owner}/${repo}/issues/${r30["number"].text}", obj("state" to "closed", "state_reason" to "completed"))
val r159 = call("POST", "https://api.github.com/repos/${owner}/${repo}/issues/${r37["number"].text}/comments", obj("body" to "Same crash as #${r3["number"].text}."))
val r172 = call("PATCH", "https://api.github.com/repos/${owner}/${repo}/issues/${r37["number"].text}", obj("state" to "closed", "state_reason" to "duplicate", "duplicate_issue_id" to r3["id"]))
println("done")
