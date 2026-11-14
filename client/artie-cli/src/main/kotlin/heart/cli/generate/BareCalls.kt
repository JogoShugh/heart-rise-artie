package heart.cli.generate

import heart.cli.BadArguments
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * One call the gateway made to the service for a run, from its upstream record (GATEWAY_RECORD): its [number]
 * among the run's calls, from 1, the [span] of the run's request it served, and the request and answer as they went.
 */
internal class UpstreamCall(val number: Int, val span: String, doc: Map<*, *>) {
    private val request = doc["request"].map()
    private val response = doc["response"].map()
    val method: String = request["method"].toString()
    val url: String = request["url"].toString()
    val body: Any? = request["body"]
    val answer: Any? = response["body"]
    val taken: Boolean = (response["status"] as? Number)?.toInt() in TAKEN
    val writes: Boolean get() = method != "GET" && taken

    /** Where [value] stands in the answer, by JSON Pointer, each one this call answered with and wasn't sent. */
    fun pointers(value: String): List<String> =
        if (!taken || value in url || value in body.toString()) emptyList()
        else leaves(answer, "").filter { (_, leaf) -> leaf !is Boolean && leaf?.toString() == value }.map { it.first }

    private fun leaves(node: Any?, at: String): List<Pair<String, Any?>> = when (node) {
        is Map<*, *> -> node.entries.flatMap { (k, v) -> leaves(v, "$at/$k") }
        is List<*> -> node.flatMapIndexed { i, v -> leaves(v, "$at/$i") }
        else -> listOf(at to node)
    }

    private companion object {
        val TAKEN = 200..299
    }
}

/**
 * A run as the calls it came down to (spec/ideas/bare-runs.md): the gateway's [calls] to the service under the
 * run's trace, found by the traceparents its capture kept, and the spans of the steps the run waited before.
 */
internal class BareRun(val capture: Capture, upstream: String) {
    private val traceparents = capture.steps.flatMap { it.traceparents }
    private val traceId: String = traceparents.firstOrNull()?.split('-')?.getOrNull(1)
        ?: throw BadArguments(
            "can't generate a bare script from ${capture.name}: it keeps no exchanges; record the run with --exchanges",
        )

    val calls: List<UpstreamCall> = Yaml(LoaderOptions().apply { codePointLimit = Int.MAX_VALUE })
        .loadAll(upstream).map { it.map() }
        .filter { it["traceparent"]?.toString()?.split('-')?.getOrNull(1) == traceId }
        .mapIndexed { i, doc -> UpstreamCall(i + 1, doc["traceparent"].toString().split('-')[2], doc) }

    /** The spans of each step that came right after an until: the run waited before its first write. */
    private val waitedBefore: Set<String> = capture.steps.zipWithNext()
        .filter { (step, _) -> step.say == "until" }
        .flatMap { (_, next) -> next.traceparents.map { it.split('-')[2] } }.toSet()

    /** The writes the service took, in order, each the first of its step after an until marked as waited for. */
    val writes: List<Pair<UpstreamCall, Boolean>> = calls.filter { it.writes }.let { writes ->
        val first = waitedBefore.mapNotNull { span -> writes.firstOrNull { it.span == span } }.toSet()
        writes.map { it to (it in first) }
    }

    /** Where [value] can be read from before the call [before]: an earlier answer and a pointer, the best first. */
    fun source(value: String, before: Int): Pair<UpstreamCall, String>? = calls.take(before - 1)
        .flatMap { call -> call.pointers(value).map { call to it } }
        .minWithOrNull(compareBy({ (_, at) -> at.split('/').any { it.isNotEmpty() && it.all(Char::isDigit) } },
            { (call, _) -> !call.writes }, { (call, _) -> call.number }))
}
