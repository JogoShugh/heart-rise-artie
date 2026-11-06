package com.artie.heart.sims

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.ConcurrentHashMap

/**
 * What a service does wrong, set by a scenario and cleared by a reset: the
 * answers a correct HEART service never gives, but a client must still
 * handle. A filter in front of the service's own handler; each fault left
 * unset changes nothing. What its representations say wrong is [body].
 */
class Faults {
    /** Rewrites of what each representation says. */
    val body = BodyFaults()

    /** An answer to every act at a path ending `/actions/{act}`, by act. */
    private val acts = ConcurrentHashMap<String, Response>()

    /** Plain resources served at a path, in place of whatever the service has there. */
    private val plain = ConcurrentHashMap<String, Response>()

    /** A Preference-Applied each read says, whatever the service applied. */
    @Volatile var applied: String? = null

    /** Whether each read leaves out its profile link. */
    @Volatile var noProfile = false

    /** A media type each read says instead. */
    @Volatile var mediaType: String? = null

    /** The one media type the service answers in, whatever a request accepts. */
    @Volatile var onlyAccept: String? = null

    /** A status every read is answered with, instead of the resource. */
    @Volatile var readStatus: Int? = null

    /** Whether each read leaves out its validator. */
    @Volatile var noValidator = false

    /** Whether each act is answered with no body: 204, as a Jenkins trigger answers. */
    @Volatile var actsWithNoBody = false

    /** The resource each act's answer names in Content-Location. */
    @Volatile var actsFrom: String? = null

    /** Answers act [act] with [status] and [body]: a problem when the body is JSON, plain text otherwise. */
    fun answerAct(act: String, status: Int, body: String) {
        val type = if (body.trimStart().startsWith("{")) PROBLEM_JSON else "text/plain"
        acts[act] = Response(statusOf(status)).header("Content-Type", type).body(body)
    }

    /** Serves [body] at [path] with [status], as JSON, with [validator] as its ETag when given. */
    fun serve(path: String, status: Int, body: String, validator: String? = null) {
        val answer = Response(statusOf(status)).header("Content-Type", "application/json").body(body)
        plain[path] = validator?.let { answer.header("ETag", it) } ?: answer
    }

    /** Declares [schema] for the form [key] in every read, while the service goes on checking its own. */
    fun declareSchema(key: String, schema: String) {
        body.declared[key] = body.json(schema)
    }

    /** Carries [template] as the HAL-FORMS template [name] in every read. */
    fun template(name: String, template: String) {
        body.templates[name] = body.json(template)
    }

    fun clear() {
        body.clear()
        acts.clear()
        plain.clear()
        applied = null
        noProfile = false
        mediaType = null
        onlyAccept = null
        readStatus = null
        noValidator = false
        actsWithNoBody = false
        actsFrom = null
    }

    val filter = Filter { next -> { request -> answer(request, next) } }

    private fun answer(request: Request, next: HttpHandler): Response {
        val acting = request.method != Method.GET && request.method != Method.HEAD
        if (acting) acts[request.uri.path.substringAfterLast("/actions/", "")]?.let { return it }
        plain[request.uri.path]?.let { return it }
        readStatus?.takeIf { !acting }?.let { return Response(statusOf(it)) }
        if (body.oneAddress && request.uri.path == BodyFaults.ONE_ADDRESS) {
            return next(request.uri(request.uri.path(checkNotNull(body.behindOneAddress) { "no set linked yet" })))
        }
        val asked = onlyAccept?.let { request.replaceHeader("Accept", it) } ?: request
        val answer = rewritten(next(asked))
        return if (acting) actAnswer(answer) else answer
    }

    private fun actAnswer(answer: Response): Response {
        var shaped = answer
        if (actsWithNoBody && answer.status.successful) {
            shaped = Response(Status.NO_CONTENT).headers(answer.headers).removeHeader("Content-Type").body("")
        }
        actsFrom?.let { shaped = shaped.replaceHeader("Content-Location", it) }
        return shaped
    }

    private fun rewritten(response: Response): Response {
        var answer = response
        applied?.let { answer = answer.replaceHeader("Preference-Applied", it) }
        mediaType?.let { answer = answer.replaceHeader("Content-Type", it) }
        if (noProfile) answer = answer.removeHeader("Link")
        if (noValidator) answer = answer.removeHeader("ETag")
        // Only JSON is reshaped; anything else, such as Swagger UI's script, streams through unread.
        if ("json" !in answer.header("Content-Type").orEmpty()) return answer
        val text = answer.bodyString()
        return answer.body(body.rewritten(text) ?: text)
    }

    companion object {
        const val PROBLEM_JSON = "application/problem+json"

        private fun statusOf(code: Int) = Status.fromCode(code) ?: Status(code, "")
    }
}
