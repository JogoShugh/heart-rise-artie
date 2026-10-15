package com.artie.heart.coap

import com.artie.heart.ActPipeline
import com.artie.heart.Dialect
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.Rung
import com.artie.heart.Search
import com.artie.heart.SearchPages
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.artie.heart.SharedContent
import com.artie.heart.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.californium.core.coap.CoAP.Code
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.Request
import org.eclipse.californium.core.coap.Response

/**
 * Every declared resource type over CoAP (SPEC §6.0.1): the same resources as
 * over HTTP, from the same declarations, in the hal-schema-forms dialect.
 * `/` is the service index, with its own acts at `/actions/{path}`;
 * `/{collection}` a search; `/{collection}/{id}` Layer 1, with its acts at
 * `/{collection}/{id}/actions/{path}`; and the
 * content-addressed layers and profiles at the same paths as over HTTP.
 */
internal class CoapRoutes(
    resources: List<HeartResource<*, *>>,
    private val index: ServiceIndex,
    private val limits: ServingLimits,
    mapper: ObjectMapper,
) {
    private val replies = CoapReplies(mapper)
    private val types: Map<String, Typed> = resources.associate { it.layers.type.collection to typed(it, mapper) }
    private val layers: List<Layers<*, *>> = resources.map { it.layers }
    private val content = SharedContent(layers)
    private val profiles: Map<String, String> = layers.map { it.type.profile }.distinctBy { it.name }
        .associate { it.name to mapper.writeValueAsString(it.alps) }

    suspend fun handle(request: Request): Response =
        runCatching { route(request) }.getOrElse { failure -> replies.refusal(failure) ?: throw failure }

    private suspend fun route(request: Request): Response {
        val path = request.options.uriPathString
        val segments = path.split('/').filter { it.isNotEmpty() }
        val typed = segments.firstOrNull()?.let { types[it] }
        return when {
            !acceptable(request) -> replies.error(ResponseCode.NOT_ACCEPTABLE, "only hal-schema-forms is served")
            request.code == Code.POST -> post(request, typed, segments)
            request.code != Code.GET -> replies.error(ResponseCode.METHOD_NOT_ALLOWED, "${request.code} $path")
            typed != null && segments.size == 1 -> search(typed, request)
            typed != null && segments.size == 2 -> typed.read(request, id(segments[1]))
            else -> read(request, segments) ?: replies.error(ResponseCode.NOT_FOUND, path)
        }
    }

    /** An act at `/{collection}/{id}/actions/{path}`, or one the bookmark offers at `/actions/{path}`. */
    private suspend fun post(request: Request, typed: Typed?, segments: List<String>): Response {
        val atBookmark = segments.size == 2 && segments[0] == ACTIONS
        val owner = if (atBookmark) types.values.firstOrNull { it.offersAtBookmark(segments[1]) } else null
        return when {
            typed != null && segments.size == ACT_SEGMENTS && segments[2] == ACTIONS ->
                typed.act(request, id(segments[1]), segments[ACT_SEGMENTS - 1])
            owner != null -> owner.actAtBookmark(request, segments[1])
            atBookmark -> replies.error(ResponseCode.NOT_FOUND, request.options.uriPathString)
            else -> replies.error(ResponseCode.METHOD_NOT_ALLOWED, "POST ${request.options.uriPathString}")
        }
    }

    private fun read(request: Request, segments: List<String>): Response? {
        val address = segments.getOrNull(1)
        return when (segments.firstOrNull()) {
            null -> serviceIndex(request)
            "affordance-sets" -> immutable(content.affordanceSets[address], HAL)
            "schemas" -> immutable(content.schemas[address], CoapOptions.SCHEMA_JSON)
            "unavailable" -> immutable(content.explanations[address], CoapOptions.JSON)
            "profiles" -> profiles[address]?.let { replies.document(it, CoapOptions.ALPS_JSON, DAY) }
            else -> null
        }
    }

    private fun serviceIndex(request: Request): Response =
        if (request.options.hasAccept() && request.options.accept == CoapOptions.JSON_HOME) {
            replies.document(index.home(limits.maxRung), CoapOptions.JSON_HOME, maxAge = 0)
        } else {
            val validator = CoapOptions.validator(index.validator(Dialect.JSONSCHEMA))
            replies.layer1(ResponseCode.CONTENT, index.body(Dialect.JSONSCHEMA), validator, null)
        }

    private suspend fun search(typed: Typed, request: Request): Response {
        val find = typed.resource.find ?: return replies.error(ResponseCode.NOT_FOUND, typed.collection)
        val query = request.options.uriQuery.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val search = Search(typed.collection, query["name"], query["id"]?.toLongOrNull())
        val page = query["page"]?.toIntOrNull() ?: 1
        val body = SearchPages.page(search, find(search.name, search.id), page, limits.pageSize)
        return replies.layer1(ResponseCode.CONTENT, body, null, null)
    }

    private fun immutable(body: String?, format: Int): Response? = body?.let { replies.immutable(it, format) }

    private fun rung(request: Request): Rung =
        limits.cap(Rung.fromPrefer(request.options.getOtherOption(CoapOptions.PREFER)?.stringValue))

    /** One declared type, with its state type fixed, so its states can be read, acted on and rendered. */
    private inner class Typed(val resource: HeartResource<Any?, Any?>, mapper: ObjectMapper) {
        val collection = resource.layers.type.collection
        private val pipeline = ActPipeline(resource.layers.type, mapper)

        suspend fun read(request: Request, id: Long): Response {
            val state = resource.load(id)
            val rung = rung(request)
            val validator = validatorOf(state, rung)
            if (request.options.eTags.any { it.contentEquals(validator) }) return replies.valid(validator)
            val body = resource.layers.layer1(state, rung)
            return replies.layer1(ResponseCode.CONTENT, body, validator, rung.preferenceApplied)
        }

        /** SPEC §6.2 over CoAP: the same checks, in the same order, with the 8-byte validator. */
        suspend fun act(request: Request, id: Long, path: String): Response {
            val act = resource.layers.type.acts.firstOrNull { it.path == path }
                ?: return replies.error(ResponseCode.NOT_FOUND, path)
            val rung = rung(request)
            val held = request.options.ifMatch.takeIf { it.isNotEmpty() }
            val checked = pipeline.check(act.name, held, { resource.load(id) }) { current, validators ->
                validators.any { it.contentEquals(validatorOf(current, rung)) }
            }
            // The act is performed on the state whose validator matched, so a write since is stale.
            val done = pipeline.run(act.name, request.payloadString) { input ->
                resource.perform(checked, act.name, input)
            }
            val body = resource.layers.layer1(done.state, rung)
            return replies.layer1(ResponseCode.CHANGED, body, validatorOf(done.state, rung), rung.preferenceApplied)
        }

        fun offersAtBookmark(path: String): Boolean = resource.layers.type.bookmarkActs.any { it.path == path }

        /**
         * The bookmark's act at `/actions/{path}`. Its owning resource is the
         * service index, so If-Match carries the index's 8-byte validator. The
         * act brings a resource into being, so the answer is 2.01 Created, with
         * its Location-Path and its representation, whose self link names it too.
         */
        suspend fun actAtBookmark(request: Request, path: String): Response {
            val act = resource.layers.type.bookmarkActs.first { it.path == path }
            val rung = rung(request)
            val index = CoapOptions.validator(index.validator(Dialect.JSONSCHEMA))
            pipeline.checkAtBookmark(act.name, request.options.ifMatch.takeIf { it.isNotEmpty() }) { validators ->
                validators.any { it.contentEquals(index) }
            }
            val done = pipeline.run(act.name, request.payloadString) {
                checkNotNull(resource.performAtBookmark)(act.name, it)
            }
            val id = resource.layers.type.state.idOf(done.state)
            val body = resource.layers.layer1(done.state, rung)
            return replies.layer1(ResponseCode.CREATED, body, validatorOf(done.state, rung), rung.preferenceApplied)
                .apply { options.setLocationPath("$collection/$id") }
        }

        private fun validatorOf(state: Any?, rung: Rung): ByteArray = CoapOptions.validator(
            StateETag.of(
                resource.layers.type.state.versionOf(state), Dialect.JSONSCHEMA.etagTag, rung,
                resource.layers.layer1(state, rung),
            ),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun typed(resource: HeartResource<*, *>, mapper: ObjectMapper): Typed =
        Typed(resource as HeartResource<Any?, Any?>, mapper)

    private companion object {
        const val ACT_SEGMENTS = 4
        const val ACTIONS = "actions"
        const val DAY = 86_400L
        const val HAL = CoapOptions.HAL_JSON
        val FORMATS = with(CoapOptions) { setOf(HAL_JSON, JSON_HOME, ALPS_JSON, SCHEMA_JSON, JSON) }

        fun id(segment: String): Long =
            segment.toLongOrNull() ?: throw ResourceNotFoundException("no resource $segment")

        /** The only dialect served over CoAP is hal-schema-forms (SPEC §6.0.1). */
        fun acceptable(request: Request): Boolean {
            val formatOk = !request.options.hasAccept() || request.options.accept in FORMATS
            val profiles = request.options.getOthers(CoapOptions.ACCEPT_PROFILE).map { it.stringValue.trim('<', '>') }
            return formatOk && (profiles.isEmpty() || Dialect.JSONSCHEMA.profile in profiles)
        }
    }
}
