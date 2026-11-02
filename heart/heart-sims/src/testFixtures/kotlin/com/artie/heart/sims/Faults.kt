package com.artie.heart.sims

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.util.concurrent.ConcurrentHashMap

/** How a service links its affordance set instead: under [rel], with the curie [curie] for [href], or none. */
data class SetLink(val rel: String, val curie: String? = null, val href: String? = null)

/**
 * What a service does wrong, set by a scenario and cleared by a reset: the
 * answers a correct HEART service never gives, but a client must still
 * handle. A filter in front of the service's own handler; each fault left
 * unset changes nothing.
 */
class Faults {
    private val mapper = ObjectMapper()

    /** An answer to every act at a path ending `/actions/{act}`, by act. */
    private val acts = ConcurrentHashMap<String, Response>()

    /** A schema each read declares for a form, by its key, in place of the one the service checks. */
    val declared = ConcurrentHashMap<String, JsonNode>()

    /** A HAL-FORMS template each read carries, by its name, in place of the one the service renders. */
    val templates = ConcurrentHashMap<String, JsonNode>()

    /** A Preference-Applied each read says, whatever the service applied. */
    @Volatile var applied: String? = null

    /** Where each read links the affordance set instead. */
    @Volatile var setLink: SetLink? = null

    /** Whether each read links the affordance set with an integrity its bytes do not match. */
    @Volatile var wrongIntegrity = false

    /** Whether every affordance set is linked at one address, whatever it holds. */
    @Volatile var oneAddress = false

    /** Whether each read leaves out its profile link. */
    @Volatile var noProfile = false

    /** A media type each read says instead. */
    @Volatile var mediaType: String? = null

    /** The one media type the service answers in, whatever a request accepts. */
    @Volatile var onlyAccept: String? = null

    /** Plain resources served at a path, in place of whatever the service has there. */
    private val plain = ConcurrentHashMap<String, Response>()

    /** Serves [body] at [path] with [status], as JSON, with [validator] as its ETag when given. */
    fun serve(path: String, status: Int, body: String, validator: String? = null) {
        val answer = Response(Status(status, "")).header("Content-Type", "application/json").body(body)
        plain[path] = validator?.let { answer.header("ETag", it) } ?: answer
    }

    /** The set behind the one address, as last linked. */
    @Volatile private var behindOneAddress: String? = null

    /** Answers act [act] with [status] and [body]: a problem when the body is JSON, plain text otherwise. */
    fun answerAct(act: String, status: Int, body: String) {
        val type = if (body.trimStart().startsWith("{")) PROBLEM_JSON else "text/plain"
        acts[act] = Response(Status(status, "")).header("Content-Type", type).body(body)
    }

    /** Declares [schema] for the form [key] in every read, while the service goes on checking its own. */
    fun declareSchema(key: String, schema: String) {
        declared[key] = mapper.readTree(schema)
    }

    /** Carries [template] as the HAL-FORMS template [name] in every read. */
    fun template(name: String, template: String) {
        templates[name] = mapper.readTree(template)
    }

    fun clear() {
        acts.clear()
        declared.clear()
        templates.clear()
        applied = null
        setLink = null
        wrongIntegrity = false
        oneAddress = false
        noProfile = false
        mediaType = null
        onlyAccept = null
        plain.clear()
        behindOneAddress = null
    }

    val filter = Filter { next -> { request -> answer(request, next) } }

    private fun answer(request: Request, next: HttpHandler): Response {
        val act = request.uri.path.substringAfterLast("/actions/", "").takeIf { request.method != Method.GET }
        act?.let { acts[it] }?.let { return it }
        plain[request.uri.path]?.let { return it }
        if (oneAddress && request.uri.path == ONE_ADDRESS) {
            return next(request.uri(request.uri.path(checkNotNull(behindOneAddress) { "no set linked yet" })))
        }
        val asked = onlyAccept?.let { request.replaceHeader("Accept", it) } ?: request
        return rewritten(next(asked))
    }

    private fun rewritten(response: Response): Response {
        var answer = response
        applied?.let { answer = answer.replaceHeader("Preference-Applied", it) }
        mediaType?.let { answer = answer.replaceHeader("Content-Type", it) }
        if (noProfile) answer = answer.removeHeader("Link")
        val body = (runCatching { mapper.readTree(answer.bodyString()) }.getOrNull() as? ObjectNode) ?: return answer
        if (!rewrite(body)) return answer
        return answer.body(mapper.writeValueAsString(body))
    }

    /** Applies each fault that changes [body]; whether any did. */
    private fun rewrite(body: ObjectNode): Boolean {
        var changed = false
        (body.get("_forms") as? ObjectNode)?.let { forms ->
            declared.forEach { (key, schema) -> (forms.get(key) as? ObjectNode)?.set<JsonNode>("schema", schema) }
            changed = changed || declared.isNotEmpty()
        }
        (body.get("_templates") as? ObjectNode)?.let { forms ->
            templates.forEach { (name, template) -> forms.set<JsonNode>(name, template) }
            changed = changed || templates.isNotEmpty()
        }
        val links = body.get("_links") as? ObjectNode ?: return changed
        val set = links.get(FORMS) as? ObjectNode ?: return changed
        if (wrongIntegrity) set.put("integrity", WRONG_INTEGRITY)
        if (oneAddress) {
            behindOneAddress = set.get("href").asText()
            set.put("href", ONE_ADDRESS)
        }
        setLink?.let { relinked(links, set, it) }
        return true
    }

    private fun relinked(links: ObjectNode, set: ObjectNode, link: SetLink) {
        links.remove(FORMS)
        links.set<JsonNode>(link.rel, set)
        val kept = (links.get("curies") as? ArrayNode)?.filterNot { it.path("name").asText() == HR }.orEmpty()
        val curies: ArrayNode = links.putArray("curies")
        kept.forEach { curies.add(it) }
        if (link.curie != null) {
            curies.addObject().put("name", link.curie).put("href", link.href).put("templated", true)
        }
    }

    companion object {
        const val PROBLEM_JSON = "application/problem+json"
        private const val FORMS = "hr:forms"
        private const val HR = "hr"
        private const val ONE_ADDRESS = "/affordance-sets/the-one"
        private const val WRONG_INTEGRITY = "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
