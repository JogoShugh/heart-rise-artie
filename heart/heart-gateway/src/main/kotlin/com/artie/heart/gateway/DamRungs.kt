package com.artie.heart.gateway

import com.artie.heart.BodyCheck
import com.artie.heart.UnprocessableContentException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * The described, connector and offered rungs (spec/ideas/gateway-pass-through.md): what the owner's gateway refuses
 * of a request passing through, before the upstream is asked. [spec] holds the operations the connector lists, each
 * with GitHub's own description of its body; [connector] holds each resource's acts, the operation behind each and
 * the values it fixes, and the rules of what is offered when, with their reasons, which the gateway's own HEART
 * resources serve.
 */
internal class DamRungs(spec: GatewaySpec, private val connector: Connector?, private val mapper: ObjectMapper) {
    private val routes = spec.routes.values.map { it to pattern(it.path) }

    /** Each declared resource with the pattern of the path it is read at. */
    private val owners = connector?.resources.orEmpty().mapNotNull { resource ->
        spec.routes[resource.read]?.let { resource to pattern(it.path) }
    }

    private val checks = mutableMapOf<String, BodyCheck>()

    /** What [dam]'s rungs make of [request], with [heart] the gateway's own resources: a refusal, or null to pass. */
    fun refusal(request: Request, dam: Dam, heart: HttpHandler): Response? {
        val route = routes.firstOrNull { (route, path) ->
            route.method == request.method.name && path.matches(request.uri.path)
        }?.first
        val refused = when {
            route == null -> Status.FORBIDDEN to unlisted(request).takeIf { Dam.Rung.CONNECTOR in dam }
            Dam.Rung.DESCRIBED in dam && invalid(route, request) != null ->
                Status.UNPROCESSABLE_ENTITY to invalid(route, request)
            Dam.Rung.OFFERED in dam && request.method != Method.GET ->
                Status.CONFLICT to notOffered(route, request, heart)
            else -> null
        }
        return refused?.let { (status, detail) -> detail?.let { damRefusal(status, it) } }
    }

    private fun unlisted(request: Request) =
        "the owner's gateway passes no ${request.method} ${request.uri.path} through: " +
            "its connector lists no such operation"

    /** What breaks GitHub's own description of [route]'s body in [request]'s; null when nothing does. */
    private fun invalid(route: SpecRoute, request: Request): String? {
        val schema = route.body ?: return null
        val check = checks.getOrPut(route.operation.name) { BodyCheck(mapper.writeValueAsString(schema)) }
        return try {
            check.check(request.bodyString())
            null
        } catch (broken: UnprocessableContentException) {
            "the body breaks GitHub's description of ${route.operation.name}: " +
                broken.violations.joinToString("; ") { "${it.pointer}: ${it.message}" }
        }
    }

    /**
     * Why the act [request] is, on the resource it writes, is not offered now, in the connector's own words; null
     * when it is, or when no act of a declared resource is it. The act is the one of [route]'s operation whose
     * fixed values the body agrees with most; with none named, the one that fixes none.
     */
    private fun notOffered(route: SpecRoute, request: Request, heart: HttpHandler): String? {
        val (resource, at) = owner(request.uri.path) ?: return null
        val body = mapper.readTree(request.bodyString().ifBlank { "{}" })
        return act(resource, route.operation.name, body)?.let { unavailable(at, request, heart)[it] }
    }

    /** The declared resource [path] writes to, and its address: the longest start of [path] one is read at. */
    private fun owner(path: String): Pair<ResourceDeclaration, String>? {
        val segments = path.trimEnd('/').split('/')
        return (segments.size downTo 2).asSequence().map { segments.take(it).joinToString("/") }
            .firstNotNullOfOrNull { at -> owners.firstOrNull { (_, read) -> read.matches(at) }?.let { it.first to at } }
    }

    private fun act(resource: ResourceDeclaration, operation: OperationId, body: JsonNode): String? {
        val acts = resource.acts.filter { it.operation == operation }
        val agreed = acts.associateWith { act -> agreement(act.fixed, body) }.filterValues { it != null }
        val named = agreed.filterValues { it!! > 0 }
        return (named.maxByOrNull { it.value!! } ?: agreed.entries.firstOrNull { it.key.fixed.isEmpty() })?.key?.name
    }

    /** How many of [fixed]'s values [body] names, all agreeing; null when one disagrees. Templates aren't held to. */
    private fun agreement(fixed: Map<String, Any?>, body: JsonNode): Int? {
        val named = fixed.filter { (name, value) -> body.has(name) && !"$value".contains('{') }
        return named.size.takeIf { named.all { (name, value) -> body.path(name).asText() == "$value" } }
    }

    /** The acts unavailable on the resource at [at], read as HEART as [request]'s caller: act to reason. */
    private fun unavailable(at: String, request: Request, heart: HttpHandler): Map<String, String> {
        val asCaller = { r: Request -> request.header("Authorization")?.let { r.header("Authorization", it) } ?: r }
        val read = heart(asCaller(Request(Method.GET, at).header("Accept", "application/hal+json")))
        val href = mapper.readTree(read.bodyString()).path("_links").path("hr:unavailable").path("href").asText()
        if (href.isEmpty()) return emptyMap()
        val explained = mapper.readTree(heart(asCaller(Request(Method.GET, href))).bodyString())
        return explained.path("unavailable").associate { absent ->
            absent.path("rel").asText().substringAfter('#') to absent.path("reason").asText()
        }
    }

    private companion object {
        /** A path template as a pattern: each variable one segment. */
        fun pattern(template: String) = Regex(template.replace(Regex("""\{[^}]+}"""), "[^/]+"))
    }
}
