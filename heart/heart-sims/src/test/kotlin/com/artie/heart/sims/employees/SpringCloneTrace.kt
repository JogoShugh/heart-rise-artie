package com.artie.heart.sims.employees

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Test
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Proves the employees simulator, answering as stock Spring (`/_sim/employees/mode`), is a clone of Spring HATEOAS's
 * own affordances example: every request goes to the clone and to the example itself, run untouched, and the run
 * stops at the first answer that differs in status, Content-Type, Location, ETag or body, byte for byte once the
 * example's origin is written as the clone's (both write every link absolute, against the address they were asked
 * at). It writes the trace for the run report, each step's comparison the example's own answer, only when
 * SPRING_CLONE_OUT names a folder, with SPRING_REAL and SPRING_CLONE the two origins.
 */
class SpringCloneTrace {
    @Test
    fun writesTheComparison() {
        val out = System.getenv("SPRING_CLONE_OUT")?.let(::File) ?: return
        val real = System.getenv("SPRING_REAL") ?: "http://localhost:8097"
        val clone = System.getenv("SPRING_CLONE") ?: "http://localhost:8096"
        out.mkdirs()
        File(out, "trace.yaml").writeText(CloneRun(real, clone).trace())
    }
}

private class CloneRun(private val real: String, private val clone: String) {
    private val mapper = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val client = JavaHttpClient()
    private val steps = mutableListOf<Map<String, Any?>>()

    fun trace(): String {
        val reset = client(Request(Method.POST, "$clone/_sim/reset"))
        check(reset.status.successful) { "the clone did not reset" }
        check(client(Request(Method.PUT, "$clone/_sim/employees/mode").body("stock-spring")).status.successful)
        read()
        acts()
        return yaml.writeValueAsString(mapOf("steps" to steps))
    }

    /** What each media type gets, and what a client's validators get: nothing to revalidate with. */
    private fun read() {
        get("The entry, asked for as {accept}", "/employees", HAL_FORMS)
        get("The entry, asked for as {accept}: Spring's own JSON, links in an array, no forms", "/employees", HAL)
        get("The entry, asked for as {accept}: HAL-FORMS", "/employees", ANY)
        get("Employee {id}, as {accept}", "/employees/1", HAL_FORMS, mapOf("id" to 1))
        get("Employee {id}, as {accept}", "/employees/1", HAL, mapOf("id" to 1))
        send(
            "Employee {id} again, with If-None-Match: no ETag was given, so nothing to revalidate, read in full",
            Request(Method.GET, "/employees/1").header("Accept", HAL_FORMS).header("If-None-Match", "\"1\""),
            mapOf("id" to 1),
        )
        get("Employee {id}, who never was", "/employees/$NEVER", HAL_FORMS, mapOf("id" to NEVER), expect = 404)
    }

    /** The templates' acts, sent upper case (Tomcat refuses `post`), each answered 204 with no body. */
    private fun acts() {
        send(
            "Act {relation} on the entry: hire {firstName}, by POST, answered with no body",
            json(Method.POST, "/employees", SAMWISE), mapOf("relation" to "default", "firstName" to "Samwise"), 204,
        )
        get("The entry again: {count} employees", "/employees", HAL_FORMS, mapOf("count" to 3))
        send(
            "Act {relation} on employee {id}: {role}, by PUT, with an If-Match that matches nothing, taken anyway",
            json(Method.PUT, "/employees/1", FRODO_RETIRED).header("If-Match", "\"stale\""),
            mapOf("relation" to "default", "id" to 1, "role" to "ring bearer, retired"), 204,
        )
        get("Employee {id}, as changed", "/employees/1", HAL_FORMS, mapOf("id" to 1))
        send(
            "Act {relation} on employee {id}, by DELETE, answered with no body",
            Request(Method.DELETE, "/employees/1"), mapOf("relation" to "deleteEmployee", "id" to 1), 204,
        )
        get("Employee {id}, gone", "/employees/1", HAL_FORMS, mapOf("id" to 1), expect = 404)
        get("The entry at the end", "/employees", HAL_FORMS)
    }

    private fun get(
        say: String,
        path: String,
        accept: String,
        params: Map<String, Any?> = emptyMap(),
        expect: Int = 200,
    ) = send(say, Request(Method.GET, path).header("Accept", accept), params + ("accept" to accept), expect)

    private fun json(method: Method, path: String, body: String) =
        Request(method, path).header("Content-Type", JSON).header("Accept", HAL_FORMS).body(body)

    /** [request] to the clone and to the example; recorded, and refused here if the two answers differ. */
    private fun send(say: String, request: Request, params: Map<String, Any?>, expect: Int = 200) {
        val at = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val started = System.nanoTime()
        val cloned = client(request.uri(request.uri.scheme("http").authority(authority(clone))))
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        val original = client(request.uri(request.uri.scheme("http").authority(authority(real))))
        check(cloned.status.code == expect) { "$say: the clone answered ${cloned.status.code}, expected $expect" }
        same(say, cloned, original)
        steps += linkedMapOf(
            "at" to at,
            "mode" to "script",
            "say" to say,
            "params" to params,
            "request" to requestOf(request, clone),
            "response" to responseOf(cloned, clone),
            "compare" to mapOf(
                "label" to "Spring HATEOAS's affordances example, commit c100c56, at $real",
                "diff" to true,
                "request" to requestOf(request, real),
                "response" to responseOf(original, real),
            ),
            "summary" to "${verdictOf(cloned)}, as Spring's example answers, byte for byte",
            "expect" to expect,
            "ms" to ms,
        ) + listOfNotNull(
            // The addresses the run names itself, not judged as hypermedia: the entry, and an employee who never was.
            if (steps.isEmpty() || params["id"] == NEVER) "entry" to true else null,
        )
    }

    /** Every part a client reads, the same: each origin written alike, the bytes otherwise as sent. */
    private fun same(say: String, cloned: Response, original: Response) {
        fun part(response: Response, origin: String) = listOf(
            response.status.code.toString(),
            response.header("Content-Type").orEmpty(),
            response.header("Location").orEmpty().replace(origin, ORIGIN),
            response.header("ETag").orEmpty(),
            response.bodyString().replace(origin, ORIGIN),
        )
        val (a, b) = part(cloned, clone) to part(original, real)
        PARTS.forEachIndexed { i, name ->
            check(a[i] == b[i]) { "$say: $name differs:\n clone: ${a[i]}\n Spring: ${b[i]}" }
        }
    }

    private fun verdictOf(response: Response) = when {
        response.status.code == NO_CONTENT -> "No content"
        response.status.code == NOT_FOUND -> "Not found, with no body"
        else -> "Read"
    }

    private fun authority(origin: String) = origin.removePrefix("http://")

    private fun requestOf(request: Request, origin: String): Map<String, Any?> = linkedMapOf(
        "method" to request.method.name,
        "url" to origin + request.uri.toString(),
        "headers" to listOf("Accept", "Content-Type", "If-Match", "If-None-Match")
            .mapNotNull { name -> request.header(name)?.let { name to it } }.toMap(),
        "body" to request.bodyString().takeIf { it.isNotEmpty() }?.let { mapper.readValue(it, Any::class.java) },
    )

    private fun responseOf(response: Response, origin: String): Map<String, Any?> = linkedMapOf(
        "status" to response.status.code,
        "headers" to listOf("Content-Type", "Location", "ETag", "Content-Length")
            .mapNotNull { name -> response.header(name)?.let { name to it } }.toMap(),
        "body" to response.bodyString().takeIf { it.isNotEmpty() }?.let { mapper.readValue(it, Any::class.java) },
    ).also { check(origin.isNotEmpty()) }

    private companion object {
        const val HAL_FORMS = "application/prs.hal-forms+json"
        const val HAL = "application/hal+json"
        const val ANY = "*/*"
        const val JSON = "application/json"
        const val ORIGIN = "{origin}"
        const val NO_CONTENT = 204
        const val NOT_FOUND = 404
        const val NEVER = 99
        const val NANOS_PER_MILLI = 1_000_000L
        val PARTS = listOf("status", "Content-Type", "Location", "ETag", "body")
        const val SAMWISE = """{"firstName":"Samwise","lastName":"Gamgee","role":"gardener"}"""
        const val FRODO_RETIRED = """{"firstName":"Frodo","lastName":"Baggins","role":"ring bearer, retired"}"""
    }
}
