package com.artie.gardenbed.coap

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.service.GardenBedService
import com.artie.gardenbed.web.BedActs
import com.artie.gardenbed.web.Dialect
import com.artie.gardenbed.web.GardenProfile
import com.artie.gardenbed.web.JsonSchemaDialectSupport
import com.artie.gardenbed.web.Rung
import com.artie.gardenbed.web.ServiceIndexController
import com.artie.gardenbed.web.ServingLimits
import com.artie.gardenbed.web.StateETag
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.californium.core.coap.CoAP.Code
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.Request
import org.eclipse.californium.core.coap.Response

/**
 * The server's resources over CoAP (SPEC §6.0.1): the same resources as over
 * HTTP, from the same services and renderers, in the hal-schema-forms dialect.
 */
internal class CoapRoutes(
    private val service: GardenBedService,
    private val acts: BedActs,
    private val index: ServiceIndexController,
    private val limits: ServingLimits,
    objectMapper: ObjectMapper,
) {
    private val replies = CoapReplies(objectMapper)
    private val refusals = CoapRefusals(replies)

    suspend fun handle(request: Request): Response =
        runCatching { route(request) }.getOrElse { failure -> refusals.of(failure) ?: throw failure }

    private suspend fun route(request: Request): Response {
        val path = request.options.uriPathString
        val segments = path.split('/').filter { it.isNotEmpty() }
        val isAct = request.code == Code.POST && segments.size == ACT_SEGMENTS && segments[0] == "beds"
        return when {
            !acceptable(request) -> replies.error(ResponseCode.NOT_ACCEPTABLE, "only hal-schema-forms is served")
            isAct -> act(request, segments[1].toLong(), segments[ACT_SEGMENTS - 1])
            request.code != Code.GET -> replies.error(ResponseCode.METHOD_NOT_ALLOWED, "${request.code} $path")
            else -> read(request, segments) ?: replies.error(ResponseCode.NOT_FOUND, path)
        }
    }

    private suspend fun read(request: Request, segments: List<String>): Response? {
        val shared = segments.getOrNull(1)?.removePrefix("sha256-")
        return when (segments.firstOrNull()) {
            null -> serviceIndex(request)
            "beds" -> if (segments.size == 1) search(request) else bed(request, segments[1].toLong())
            "affordance-sets" -> immutable(JsonSchemaDialectSupport.affordanceSetsByHash[shared], CoapOptions.HAL_JSON)
            "schemas" -> immutable(JsonSchemaDialectSupport.schemasByHash[shared], CoapOptions.SCHEMA_JSON)
            "unavailable" -> immutable(JsonSchemaDialectSupport.unavailableByHash[shared], CoapOptions.JSON)
            "profiles" -> if (segments.getOrNull(1) == "garden") alps() else null
            else -> null
        }
    }

    private suspend fun bed(request: Request, id: Long): Response {
        val state = service.currentState(id)
        val rung = rung(request)
        val validator = validatorOf(state, rung)
        if (request.options.eTags.any { it.contentEquals(validator) }) return replies.valid(validator)
        val body = JsonSchemaDialectSupport.buildBedBody(state, rung)
        return replies.layer1(ResponseCode.CONTENT, body, validator, rung.preferenceApplied)
    }

    /** SPEC §6.2 over CoAP: the same checks, in the same order, with the 8-byte validator. */
    private suspend fun act(request: Request, id: Long, actionPath: String): Response {
        val affordance = BedActs.BY_PATH[actionPath] ?: return replies.error(ResponseCode.NOT_FOUND, actionPath)
        val rung = rung(request)
        val held = request.options.ifMatch
        acts.check(id, affordance, held.isNotEmpty()) { current ->
            held.any { it.contentEquals(validatorOf(current, rung)) }
        }
        val done = acts.run(id, affordance, request.payloadString)
        val body = JsonSchemaDialectSupport.buildBedBody(done.state, rung)
        return replies.layer1(ResponseCode.CHANGED, body, validatorOf(done.state, rung), rung.preferenceApplied)
    }

    private fun serviceIndex(request: Request): Response =
        if (request.options.hasAccept() && request.options.accept == CoapOptions.JSON_HOME) {
            replies.document(index.homeBody(), CoapOptions.JSON_HOME, maxAge = 0)
        } else {
            val validator = CoapOptions.validator(index.indexETag(Dialect.JSONSCHEMA))
            replies.layer1(ResponseCode.CONTENT, index.indexBody(Dialect.JSONSCHEMA), validator, null)
        }

    private suspend fun search(request: Request): Response {
        val query = request.options.uriQuery.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val body = index.searchBody(query["name"], query["id"]?.toLongOrNull(), query["page"]?.toIntOrNull() ?: 1)
        return replies.layer1(ResponseCode.CONTENT, body, null, null)
    }

    private fun rung(request: Request): Rung =
        limits.cap(Rung.fromPrefer(request.options.getOtherOption(CoapOptions.PREFER)?.stringValue))

    private fun immutable(body: String?, format: Int): Response? = body?.let { replies.immutable(it, format) }

    private fun alps(): Response = replies.document(ALPS_BODY, CoapOptions.ALPS_JSON, DAY)

    private companion object {
        const val ACT_SEGMENTS = 4
        const val DAY = 86_400L
        val ALPS_BODY: String = ObjectMapper().writeValueAsString(GardenProfile.alps)
        val FORMATS = with(CoapOptions) { setOf(HAL_JSON, JSON_HOME, ALPS_JSON, SCHEMA_JSON, JSON) }

        /** The only dialect served over CoAP is hal-schema-forms (SPEC §6.0.1). */
        fun acceptable(request: Request): Boolean {
            val formatOk = !request.options.hasAccept() || request.options.accept in FORMATS
            val profiles = request.options.getOthers(CoapOptions.ACCEPT_PROFILE).map { it.stringValue.trim('<', '>') }
            return formatOk && (profiles.isEmpty() || Dialect.JSONSCHEMA.profile in profiles)
        }

        fun validatorOf(state: GardenBedState, rung: Rung): ByteArray =
            CoapOptions.validator(StateETag.of(state.lastSequenceNo, Dialect.JSONSCHEMA.etagTag, rung))
    }
}
