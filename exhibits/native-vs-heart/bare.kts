// An ARTIE run, brought down to bare HTTP: the GitHub calls its gateway made, as a script that makes them again
// directly, with no gateway, no forms, no schema, no validators and no checks. Run and gun, like the Python.
//   artie run bare.kts record=~/.cache/artie/runs/upstream.record.yaml trace=TRACE_ID out=runs/bare-script.kts \
//     waits=runs/RUN.yaml inputs=owner=JogoShugh,repo=heart-github-test,label=bug,stamp=1022-130257
// record is the gateway's upstream record (GATEWAY_RECORD); trace the run's trace id (its traceparent's second
// part); waits the run's own trace (artie run --trace), for where it waited; inputs the run's inputs.
//
// What it keeps:
//   - every write the run made (any method but GET that was answered 2xx), in order;
//   - only the reads whose answers supply a value a later call sends: a number, an id or a sha.
// Each value sent is written as an input, as a value read from an earlier answer, or as recorded. Where the run
// waited (an `until` step), the next write is tried again, a second apart, until GitHub takes it.
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.Date
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

val record by input(question = "Where is the gateway's upstream record?")
val trace by input(question = "Which run, by trace id?")
val out by input(question = "Where to write the script?")
val waits by input(default = "", question = "Where is the run's own trace, for its waits?")
val inputs by input(question = "The run's inputs, as name=value,name=value")

val id = Regex("""^\d{3,}$""")
val sha = Regex("""^[0-9a-f]{40}$""")
val b64 = Regex("""^[A-Za-z0-9+/]{8,}={0,2}$""")

fun yaml() = Yaml(LoaderOptions().apply { codePointLimit = Int.MAX_VALUE })

fun Any?.map(): Map<String, Any?> = (this as Map<*, *>).entries.associate { it.key.toString() to it.value }

fun moment(at: Any?): Instant = when (at) {
    is Date -> at.toInstant()
    else -> Instant.parse(at.toString())
}

fun expand(path: String) = path.replaceFirst(Regex("^~"), System.getProperty("user.home"))

val calls = yaml().loadAll(File(expand(record)).readText()).filterNotNull().map { it.map() }
    .filter { trace in it["traceparent"].toString() }
require(calls.isNotEmpty()) { "no calls in $record for trace $trace" }
val until = if (waits.isEmpty()) emptyList() else yaml().loadAll(File(expand(waits)).readText()).filterNotNull()
    .map { it.map() }.filter { it["say"] == "until" }.map { moment(it["at"]) }
val given = inputs.split(",").map { it.substringBefore("=") to it.substringAfter("=") }.sortedBy { -it.second.length }

fun request(i: Int) = calls[i]["request"].map()
fun answer(i: Int) = calls[i]["response"].map()
fun ok(i: Int) = (answer(i)["status"] as Int) in 200..299
fun isRead(i: Int) = request(i)["method"] == "GET"

fun leaves(value: Any?, pointer: String = ""): List<Pair<String, Any?>> = when (value) {
    is Map<*, *> -> value.flatMap { (k, v) -> leaves(v, "$pointer/$k") }
    is List<*> -> value.flatMapIndexed { i, v -> leaves(v, "$pointer/$i") }
    else -> listOf(pointer to value)
}

// For each id-like value, where it can be read from, best first: a pointer with no list position, a write's
// answer before a read's, the earliest. A value the call was itself given is not one it answered with.
data class Source(val call: Int, val pointer: String)

val sources: Map<String, List<Source>> = calls.indices.filter(::ok).flatMap { i ->
    val sent = request(i)["url"].toString() + request(i)["body"].toString()
    leaves(answer(i)["body"]).mapNotNull { (pointer, value) ->
        val k = value?.toString() ?: return@mapNotNull null
        if (value is Boolean || !(id.matches(k) || sha.matches(k)) || k in sent) return@mapNotNull null
        Triple(k, Source(i, pointer), Triple(pointer.split("/").any { p -> p.all(Char::isDigit) && p.isNotEmpty() }, isRead(i), i))
    }
}.groupBy({ it.first }) { it }.mapValues { (_, found) ->
    found.sortedWith(compareBy({ it.third.first }, { it.third.second }, { it.third.third })).map { it.second }
}

val reads = sortedSetOf<Int>()

fun readFrom(k: String, before: Int): String? {
    val source = sources[k]?.firstOrNull { it.call < before } ?: return null
    if (isRead(source.call)) reads += source.call
    return "r${source.call}" + source.pointer.split("/").drop(1)
        .joinToString("") { if (it.all(Char::isDigit)) "[$it]" else "[\"$it\"]" }
}

fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
    .replace("\n", "\\n") + "\""

val pattern = Regex(
    (given.filter { it.second.isNotEmpty() }.map { "(?<![A-Za-z0-9])" + Regex.escape(it.second) + "(?![A-Za-z0-9])" } +
        listOf("""(?<=#)\d{3,}""", """(?<=/)\d{3,}(?=/|$)""")).joinToString("|"))

// A Kotlin expression for the string s: inputs and read values put in, the rest as recorded.
fun text(s: String, before: Int): String {
    if (b64.matches(s) && s.length % 4 == 0) {
        val decoded = runCatching { String(Base64.getDecoder().decode(s)) }.getOrNull()
        if (decoded != null && decoded.all { it >= ' ' } && Base64.getEncoder().encodeToString(decoded.toByteArray()) == s) {
            val inner = text(decoded, before)
            if (inner != quote(decoded)) return "b64($inner)"
        }
    }
    if (sha.matches(s) || id.matches(s)) readFrom(s, before)?.let { return "$it.text" }
    val built = StringBuilder()
    var last = 0
    var changed = false
    for (m in pattern.findAll(s)) {
        val expr = given.firstOrNull { it.second == m.value }?.first ?: readFrom(m.value, before)?.let { "$it.text" }
            ?: continue
        built.append(quote(s.substring(last, m.range.first)).drop(1).dropLast(1)).append("\${").append(expr).append("}")
        last = m.range.last + 1
        changed = true
    }
    if (!changed) return quote(s)
    return "\"" + built + quote(s.substring(last)).drop(1)
}

fun value(v: Any?, before: Int): String = when (v) {
    is Map<*, *> -> "obj(" + v.entries.joinToString(", ") { (k, x) -> "${quote(k.toString())} to ${value(x, before)}" } + ")"
    is List<*> -> "listOf(" + v.joinToString(", ") { value(it, before) } + ")"
    is String -> text(v, before)
    is Int, is Long -> (if (id.matches(v.toString())) readFrom(v.toString(), before) else null) ?: v.toString()
    null -> "null"
    else -> v.toString()
}

fun waited(w: Int, previous: Int): Boolean {
    val after = if (previous >= 0) moment(calls[previous]["at"]) else Instant.MIN
    return until.any { it > after && it < moment(calls[w]["at"]) }
}

val writes = calls.indices.filter { !isRead(it) && ok(it) }
val lines = sortedMapOf<Int, String>()
writes.forEachIndexed { n, w ->
    val req = request(w)
    val body = if (req["body"] != null) ", " + value(req["body"], w) else ", null"
    val retry = if (waited(w, writes.getOrElse(n - 1) { -1 })) ", retry = true" else ""
    lines[w] = "val r$w = call(\"${req["method"]}\", ${text(req["url"].toString(), w)}$body$retry)"
}
reads.forEach { r -> lines[r] = "val r$r = call(\"GET\", ${text(request(r)["url"].toString(), r)})" }

val head = """
// Made by bare.kts from ARTIE run $trace: ${lines.size} of its ${calls.size} GitHub calls, made directly.
// No gateway, no forms, no schema, no checks. Inputs as name=value; the GitHub token from GITHUB_TOKEN.
//   artie run bare-script.kts ${given.joinToString(" ") { "${it.first}=${it.second}" }}
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlinx.serialization.json.*

${given.joinToString("\n") { "val ${it.first} by input(default = ${quote(it.second)})" }}
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
    println("${'$'}method ${'$'}url")
    var failed = ""
    repeat(if (retry) 30 else 1) {
        val sent = if (body == null) HttpRequest.BodyPublishers.noBody()
        else HttpRequest.BodyPublishers.ofString(json(body).toString())
        val answer = http.send(HttpRequest.newBuilder(URI(url)).method(method, sent)
            .header("Authorization", "Bearer ${'$'}token").header("Accept", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("Content-Type", "application/json").build(),
            HttpResponse.BodyHandlers.ofString())
        if (answer.statusCode() in 200..299) return answer.body().takeIf { it.isNotBlank() }?.let(Json::parseToJsonElement)
        failed = "${'$'}method ${'$'}url: HTTP ${'$'}{answer.statusCode()} ${'$'}{answer.body().take(300)}"
        Thread.sleep(1000)
    }
    error(failed)
}

""".trimStart()

File(out).writeText(head + lines.values.joinToString("\n") + "\nprintln(\"done\")\n")
println("${calls.size} calls recorded; kept ${writes.size} writes and ${reads.size} reads; wrote $out")
