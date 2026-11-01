package com.artie.heart.sims

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.ConcurrentHashMap

/**
 * What a service does wrong, set by a scenario and cleared by a reset: the
 * answers a correct HEART service never gives, but a client must still
 * handle. A filter in front of the service's own handler.
 */
class Faults {
    private val mapper = ObjectMapper()

    /** An answer to every act at a path ending `/actions/{act}`, by act. */
    private val acts = ConcurrentHashMap<String, Response>()

    /** Answers act [act] with [status] and [body]: a problem when the body is JSON, plain text otherwise. */
    fun answerAct(act: String, status: Int, body: String) {
        val type = if (body.trimStart().startsWith("{")) PROBLEM_JSON else "text/plain"
        acts[act] = Response(Status(status, "")).header("Content-Type", type).body(body)
    }

    /** A schema each read declares for a form, by its key, in place of the one the service checks. */
    private val declared = ConcurrentHashMap<String, JsonNode>()

    /** Declares [schema] for the form [key] in every read, while the service goes on checking its own. */
    fun declareSchema(key: String, schema: String) {
        declared[key] = mapper.readTree(schema)
    }

    fun clear() {
        acts.clear()
        declared.clear()
    }

    val filter = Filter { next ->
        { request ->
            val act = request.uri.path.substringAfterLast("/actions/", "").takeIf { request.method != Method.GET }
            act?.let { acts[it] } ?: next(request).let(::withDeclared)
        }
    }

    private fun withDeclared(response: Response): Response {
        if (declared.isEmpty() || response.header("Content-Type")?.contains("json") != true) return response
        val body = mapper.readTree(response.bodyString()) as? ObjectNode ?: return response
        val forms = body.path("_forms") as? ObjectNode ?: return response
        declared.forEach { (key, schema) -> (forms.path(key) as? ObjectNode)?.set<JsonNode>("schema", schema) }
        return response.body(mapper.writeValueAsString(body))
    }

    companion object {
        const val PROBLEM_JSON = "application/problem+json"
    }
}
