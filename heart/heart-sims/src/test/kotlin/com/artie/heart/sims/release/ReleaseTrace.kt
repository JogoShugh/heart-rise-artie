package com.artie.heart.sims.release

import com.artie.heart.Signatures
import com.artie.heart.sims.ci.CiWrapper
import com.artie.heart.sims.github.GitHubService
import com.artie.heart.sims.rise.ArtieKey
import com.artie.heart.sims.rise.OfferWatcher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.concurrent.thread

/**
 * The merge watch release, run live against its parties (`MainKt release`, the real Jenkins), as ARTIE would run
 * release.goal.md: every address after each party's entry handed over by an answer, every value from a named result.
 * Writes the run as a trace for the run report (heart-run-report) to RELEASE_TRACE_OUT, for pull request
 * RELEASE_PULL, at RELEASE_LIVE (the parties' host). It writes nothing unless all three are set.
 */
class ReleaseTrace {
    @Test
    fun writesTheRelease() {
        val host = System.getenv("RELEASE_LIVE") ?: return
        val out = System.getenv("RELEASE_TRACE_OUT")?.let(::File) ?: return
        val pull = checkNotNull(System.getenv("RELEASE_PULL")).toLong()
        out.parentFile?.mkdirs()
        out.writeText(ReleaseRun(host, pull).trace())
    }
}

/** One release of pull request [pull]: watched, merged, built, smoke tested, and promoted to Dev or not. */
private class ReleaseRun(private val host: String, private val pull: Long) {
    private val mapper = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val client = JavaHttpClient()
    private val steps = mutableListOf<Map<String, Any?>>()
    private val etags = mutableMapOf<String, String>()

    private fun at(port: Int) = "http://$host:$port"

    /** The credential ARTIE presents at each party, by port: its own tokens, and Ana's library card at the preview. */
    private val credentials = mapOf(
        Release.GITHUB_PORT to "Bearer ${GitHubService.TOKEN}",
        Release.WATCHER_PORT to "Bearer ${OfferWatcher.TOKEN}",
        Release.REPLIES_PORT to "Bearer ${Release.REPLY_TOKEN}",
        Release.CI_PORT to "Bearer ${CiWrapper.TOKEN}",
        Release.DEV_PORT to "Bearer ${DevEnvironment.TOKEN}",
        PREVIEW_PORT to "Basic " + Base64.getEncoder().encodeToString("21000451:1234".toByteArray()),
    )

    fun trace(): String {
        val pr = pullRequest()
        val merged = watchThenMerge(pr)
        val sha = merged["merge_commit_sha"].asText()
        val build = build(sha)
        if (build["state"].asText() == "completed") {
            val passed = smoke(build["result"]["_links"])
            if (passed.size == SCENARIOS) promote(sha) else comment(pr, passed)
        } else {
            comment(pr, emptyList())
        }
        return yaml.writeValueAsString(mapOf("steps" to steps))
    }

    // ---- GitHub, through the gateway: the pull request, watched until it can be merged, then merged ----

    private fun pullRequest(): JsonNode {
        control(Release.GITHUB_PORT, "/_release/github/pulls/$pull/mergeable", "null")
        val index = read("ARTIE reads GitHub's entry, through the gateway", at(Release.GITHUB_PORT) + "/", entry = true)
        val form = index["_forms"]["github:pulls-get"]
        val href = fill(form, mapOf("owner" to "acme", "repo" to "web", "pull_number" to "$pull"))
        val pr = absolute(at(Release.GITHUB_PORT), href)
        return read("ARTIE opens pull request {pull} `as pr`", pr, mapOf("pull" to pull))
    }

    private fun watchThenMerge(pr: JsonNode): JsonNode {
        val prHref = absolute(at(Release.GITHUB_PORT), pr["_links"]["self"]["href"].asText())
        val reply = "r-pr-$pull-${System.currentTimeMillis() / MILLIS}"
        openReply(reply)
        val watcher = read("ARTIE reads the offer watch's entry", at(Release.WATCHER_PORT) + "/", entry = true)
        val watch = ask(
            "ARTIE asks to be told when {pr} offers {offers}, on reply {reply} `as watch`", watcher,
            "watcher:watch", at(Release.WATCHER_PORT),
            mapOf("resource" to prHref, "offers" to MERGE, "looks" to MAX_LOOKS), reply,
            mapOf("pr" to prHref, "offers" to MERGE, "reply" to reply),
        )
        // GitHub works its mergeability out a few seconds later, on its own time.
        thread {
            Thread.sleep(WORKS_OUT_MILLIS)
            control(Release.GITHUB_PORT, "/_release/github/pulls/$pull/mergeable", "true")
        }
        val told = await(reply, "Once {watch} says it can be merged", mapOf("watch" to watch))
        check(told["state"].asText() == "completed") { "the watch was not answered: $told" }
        val about = told["result"]["_links"]["about"]["href"].asText()
        val offered = read("ARTIE follows the pull request the answer links, to merge it", about)
        val merge = offered["_forms"][MERGE]
        val squash = mapOf("merge_method" to "squash")
        act("ARTIE merges it by squashing `as merged`", prHref, merge, squash, OK, mapOf("relation" to MERGE))
        return read("ARTIE reads {pr} as merged, for its merge commit", prHref, mapOf("pr" to prHref))
    }

    // ---- Jenkins, through the CI wrapper: build the merged commit, told on a reply how it goes ----

    private fun build(sha: String): JsonNode {
        val reply = "r-build-${sha.take(SHORT)}-${System.currentTimeMillis() / MILLIS}"
        openReply(reply)
        val ci = read("ARTIE reads the CI wrapper's entry", at(Release.CI_PORT) + "/", entry = true)
        val build = ask(
            "ARTIE builds commit {commit}, asking to be told how it goes, on reply {reply} `as build`", ci,
            "ci:build", at(Release.CI_PORT), mapOf("commit" to sha), reply, mapOf("commit" to sha, "reply" to reply),
        )
        return await(reply, "Once {build} has succeeded, or failed", mapOf("build" to build))
    }

    // ---- The smoke script the build links, run against the deployment it links ----

    private fun smoke(made: JsonNode): List<String> {
        val script = made[CiWrapper.SMOKE_TEST]["href"].asText()
        send("ARTIE reads the smoke script {build} links as ci:smoke-test", 200, Request(Method.GET, script),
            mapOf("relation" to CiWrapper.SMOKE_TEST))
        val deployed = made[CiWrapper.DEPLOYMENT]["href"].asText()
        val passed = mutableListOf<String>()
        val entry = read("S1: ARTIE reads the preview {preview} the build deployed `as preview`", deployed,
            mapOf("preview" to deployed), entry = false)
        val search = fill(entry["_forms"]["library:search-loans"], mapOf("name" to "Matilda"))
        val found = read("S1: search the loans for {name}", absolute(deployed, search), mapOf("name" to "Matilda"))
        val item = found["_links"]["item"].firstOrNull { it["title"].asText() == "Matilda" }
        if (item != null) passed += "S1"
        val loanHref = absolute(deployed, checkNotNull(item) { "S1: Matilda not listed: $found" }["href"].asText())
        val loan = read("S2: open the loan {loan}", loanHref, mapOf("loan" to loanHref))
        if (loan["author"].asText() == "Roald Dahl" && loan["_forms"].has("library:renew")) passed += "S2"
        val before = etags.getValue(loanHref)
        val due = LocalDate.parse(loan["due"].asText())
        act("S3: renew it", loanHref, loan["_forms"]["library:renew"], emptyMap<String, Any>(), 200,
            mapOf("relation" to "library:renew"))
        val expected = due.plusWeeks(3).toString()
        val renewed = read("S3: read it again: due {expected}?", loanHref, mapOf("expected" to expected))
        val s3 = LocalDate.parse(renewed["due"].asText()) == due.plusWeeks(3)
        if (s3) passed += "S3" else note("S3 failed: due ${renewed["due"].asText()}, not ${due.plusWeeks(3)}")
        val stale = act(
            "S4: renew it again, with the version read before the renewal", loanHref, loan["_forms"]["library:renew"],
            emptyMap<String, Any>(), STALE, mapOf("relation" to "library:renew"), ifMatch = before,
        )
        val linksBack = mapper.readTree(stale.bodyString())["_links"]?.get("about") != null
        if (stale.status.code == STALE && linksBack) passed += "S4"
        val current = read("S5: read the loan, for its form to report a problem", loanHref)
        val report = current["_forms"]["library:report-problem"]
        val kinds = read("S5: read the kinds of problem the library lists", absolute(deployed,
            report["schema"]["properties"]["problem"]["hr:options"]["link"]["href"].asText()))
        val kind = kinds["kinds"].first { it["value"].asText() == "missing-pages" }["value"].asText()
        val reporting = mapOf("problem" to kind, "comment" to "Pages 12 to 14.")
        act("S5: report {problem} on the loan", loanHref, report, reporting, OK,
            mapOf("problem" to kind, "relation" to "library:report-problem"))
        val reported = read("S5: the loan shows the problem reported", loanHref)
        if (reported["reported"]?.get("problem")?.asText() == kind) passed += "S5"
        note("Smoke: ${passed.size} of $SCENARIOS passed (${passed.joinToString()})")
        return passed
    }

    // ---- Dev: promote the merged commit, citing the smoke run; or say on the pull request why not ----

    private fun promote(sha: String) {
        val envs = read("ARTIE reads the environments' entry", at(Release.DEV_PORT) + "/", entry = true)
        val lookup = fill(envs["_forms"]["env:get-environment"], mapOf("id" to "1"))
        val dev = read("ARTIE opens Dev", absolute(at(Release.DEV_PORT), lookup))
        val devHref = absolute(at(Release.DEV_PORT), dev["_links"]["self"]["href"].asText())
        act(
            "ARTIE promotes commit {commit} to Dev, citing the smoke run `as promotion`", devHref,
            dev["_forms"]["env:promote"],
            mapOf(
                "commit" to sha, "smoke" to mapOf("passed" to SCENARIOS, "of" to SCENARIOS),
                "_links" to mapOf("env:smoke-run" to mapOf("href" to "/reports/merge-watch-release/#pass")),
            ),
            200, mapOf("commit" to sha, "relation" to "env:promote"),
        )
        read("ARTIE reads Dev: it serves {commit}", devHref, mapOf("commit" to sha))
    }

    private fun comment(pr: JsonNode, passed: List<String>) {
        val prHref = absolute(at(Release.GITHUB_PORT), pr["_links"]["self"]["href"].asText())
        val now = read("ARTIE reads {pr}, for its form to comment", prHref, mapOf("pr" to prHref))
        val failed = (1..SCENARIOS).map { "S$it" } - passed.toSet()
        val body = "Not promoted to Dev: smoke scenarios ${failed.joinToString()} failed. See the release report."
        act("ARTIE comments on {pr} with the scenarios that failed", prHref, now["_forms"]["github:comment"],
            mapOf("body" to body), 201, mapOf("pr" to prHref, "relation" to "github:comment"))
    }

    // ---- RISE: ARTIE's replies, made before each request, and read until answered ----

    private fun openReply(reply: String) =
        control(
            Release.REPLIES_PORT, "/_release/replies/$reply", "",
            say = "ARTIE makes its reply {reply}, before asking", params = mapOf("reply" to reply),
        )

    /** Asks [relation]'s form on [entry] at [origin] with [fields], naming [reply], signed; the request's address. */
    // Each is one part of the request, as the goal names it; grouping them would only hide that.
    @Suppress("LongParameterList")
    private fun ask(
        say: String,
        entry: JsonNode,
        relation: String,
        origin: String,
        fields: Map<String, Any>,
        reply: String,
        params: Map<String, Any?>,
    ): String {
        val form = entry["_forms"][relation]
        val target = absolute(origin, form["_links"]["target"]["href"].asText())
        val body = mapper.writeValueAsString(
            fields + ("_links" to mapOf(
                "hr:reply-to" to mapOf("href" to at(Release.REPLIES_PORT) + "/replies/$reply"),
                "hr:keys" to mapOf("href" to at(Release.REPLIES_PORT) + Signatures.KEYS),
            )),
        )
        val etag = etags.getValue("$origin/")
        val signed = ArtieKey.signer.sign("POST", target, body, etag, System.currentTimeMillis() / MILLIS)
        val made = send(
            say, 201,
            Request(Method.POST, target).header("Content-Type", form["contentType"].asText()).header("If-Match", etag)
                .header("Content-Digest", signed.contentDigest).header("Signature-Input", "sig1=${signed.params}")
                .header("Signature", "sig1=:${signed.signature}:").body(body),
            params + ("relation" to relation),
        )
        val location = absolute(origin, checkNotNull(made.header("Location")))
        control(Release.REPLIES_PORT, "/_release/replies/$reply/asked", location)
        return location
    }

    /** Reads [reply] until it is answered, recording each change of it. */
    private fun await(reply: String, say: String, params: Map<String, Any?>): JsonNode {
        val href = at(Release.REPLIES_PORT) + "/replies/$reply"
        var last = ""
        repeat(AWAIT_SECONDS) {
            val now = mapper.readTree(client(authorized(Request(Method.GET, href).header("Accept", HAL))).bodyString())
            val seen = "${now["state"]?.asText()} ${now["percent"]?.asText()}"
            if (seen != last) {
                read("$say: ARTIE's reply {reply} is ${now["state"].asText()}", href, params + ("reply" to reply))
                last = seen
            }
            if (now["state"].asText() != "awaiting") return now
            Thread.sleep(SECOND)
        }
        error("$say: no answer on $reply in $AWAIT_SECONDS seconds")
    }

    // ---- Exchanges, recorded ----

    private fun read(
        say: String,
        href: String,
        params: Map<String, Any?> = emptyMap(),
        entry: Boolean = false,
    ): JsonNode {
        val answer = send(say, 200, Request(Method.GET, href).header("Accept", HAL), params, entry)
        answer.header("ETag")?.let { etags[href] = it }
        return mapper.readTree(answer.bodyString())
    }

    @Suppress("LongParameterList")
    private fun act(
        say: String,
        resource: String,
        form: JsonNode,
        fields: Map<String, Any?>,
        expect: Int,
        params: Map<String, Any?>,
        ifMatch: String = etags.getValue(resource),
    ): Response {
        val target = absolute(resource, form["_links"]["target"]["href"].asText())
        val request = Request(Method.valueOf(form["method"].asText()), target).header("If-Match", ifMatch)
            .header("Content-Type", form["contentType"]?.asText() ?: "application/json")
            .body(mapper.writeValueAsString(fields))
        return send(say, expect, request, params)
    }

    private fun control(
        port: Int,
        path: String,
        body: String,
        say: String? = null,
        params: Map<String, Any?> = emptyMap(),
    ) {
        val method = if (path.endsWith("/asked") || path.endsWith("/mergeable")) Method.PUT else Method.POST
        val request = Request(method, at(port) + path).body(body)
        if (say == null) check(client(request).status.successful) { path } else send(say, 204, request, params)
    }

    private fun note(text: String) {
        steps += linkedMapOf("at" to now(), "mode" to "script", "say" to text, "params" to emptyMap<String, Any>(),
            "summary" to text)
    }

    private fun send(
        say: String,
        expect: Int,
        request: Request,
        params: Map<String, Any?>,
        entry: Boolean = false,
    ): Response {
        val sent = authorized(request.header("Accept", request.header("Accept") ?: HAL))
        val started = System.nanoTime()
        val response = client(sent)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        check(response.status.code == expect) {
            "$say: expected $expect, got ${response.status.code}: ${response.bodyString()}"
        }
        steps += linkedMapOf(
            "at" to now(), "mode" to "script", "say" to say, "params" to params,
            "request" to linkedMapOf(
                "method" to sent.method.name, "url" to sent.uri.toString(),
                "headers" to SHOWN_ON_REQUEST.mapNotNull { n -> sent.header(n)?.let { n to redacted(n, it) } }.toMap(),
                "body" to bodyOf(sent.bodyString(), sent.header("Content-Type")),
            ),
            "response" to linkedMapOf(
                "status" to response.status.code,
                "headers" to SHOWN_ON_ANSWER.mapNotNull { n -> response.header(n)?.let { n to it } }.toMap(),
                "body" to bodyOf(response.bodyString(), response.header("Content-Type")),
            ),
            "expect" to expect, "ms" to ms,
        ) + listOfNotNull(if (entry) "entry" to true else null)
        return response
    }

    private fun authorized(request: Request): Request {
        val port = URI(request.uri.toString()).port
        return credentials[port]?.let { request.header("Authorization", it) } ?: request
    }

    private fun redacted(name: String, value: String) =
        if (name == "Authorization") value.substringBefore(' ') + " <token>" else value

    private fun bodyOf(text: String, type: String?): Any? = when {
        text.isEmpty() -> null
        type?.contains("json") == true -> runCatching { mapper.readValue(text, Any::class.java) }.getOrDefault(text)
        else -> text
    }

    /** A GET form's target filled with [values]: path variables, then a query of those it names. */
    private fun fill(form: JsonNode, values: Map<String, String>): String {
        var href = form["_links"]["target"]["href"].asText()
        values.forEach { (name, value) -> href = href.replace("{$name}", value) }
        val query = Regex("\\{\\?([^}]*)}").find(href)
        if (query != null) {
            val given = query.groupValues[1].split(",").mapNotNull { n -> values[n]?.let { "$n=$it" } }
            href = href.replace(query.value, if (given.isEmpty()) "" else "?" + given.joinToString("&"))
        }
        return href
    }

    private fun absolute(base: String, href: String) = URI(base).resolve(href).toString()

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val HAL = "application/hal+json"
        const val MERGE = "github:merge"
        const val PREVIEW_PORT = 8180
        const val SCENARIOS = 5
        const val OK = 200
        const val STALE = 412
        const val MAX_LOOKS = 40
        const val WORKS_OUT_MILLIS = 7000L
        const val AWAIT_SECONDS = 180
        const val SECOND = 1000L
        const val MILLIS = 1000L
        const val SHORT = 7
        const val NANOS_PER_MILLI = 1_000_000L
        val SHOWN_ON_REQUEST = listOf(
            "Authorization", "Accept", "If-Match", "Content-Type", "Content-Digest", "Signature-Input", "Signature",
        )
        val SHOWN_ON_ANSWER = listOf("Location", "ETag", "Content-Type", "Content-Profile", "WWW-Authenticate")
    }
}
