package heart

import org.eclipse.californium.core.CoapClient
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.Option
import org.eclipse.californium.core.coap.Request
import org.eclipse.californium.core.coap.Response
import org.eclipse.californium.core.coap.option.EmptyOptionDefinition
import org.eclipse.californium.core.coap.option.MapBasedOptionRegistry
import org.eclipse.californium.core.coap.option.StandardOptionRegistry
import org.eclipse.californium.core.coap.option.StringOptionDefinition
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.core.network.CoapEndpoint
import org.eclipse.californium.elements.config.Configuration
import org.eclipse.californium.elements.config.UdpConfig

/**
 * The CoAP binding (SPEC §6.0.1), for `coap:` URIs, over UDP with Californium.
 * The requested rendering goes in the Accept option and in the experimental
 * options Accept-Profile and Prefer; the applied rendering comes back in
 * Profile and Preference-Applied. The 8-byte CoAP ETag is the validator,
 * written in hex for the core. It keeps no cache, so a Layer 1 read always
 * reaches the server, and it says No-Cache so no proxy answers it either.
 */
class CoapBinding(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) : Binding {

    private val client: CoapClient by lazy {
        CoapConfig.register()
        UdpConfig.register()
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(Configuration.createStandardWithoutFile())
            .setOptionRegistry(REGISTRY)
            .build()
        CoapClient().setEndpoint(endpoint).setTimeout(timeoutMs)
    }

    override fun read(url: String, rendering: Rendering): Outcome =
        send(Request.newGet().apply {
            setURI(url)
            ask(rendering)
            options.addOption(Option(NO_CACHE))
        })

    override fun fetch(url: String, accept: List<String>, integrity: String?): Outcome =
        send(Request.newGet().apply {
            setURI(url)
            // CoAP's Accept takes one Content-Format; with several, the server picks.
            accept.mapNotNull { FORMATS[it] }.singleOrNull()?.let { options.accept = it }
        })

    override fun act(act: Act, rendering: Rendering): Outcome =
        send(Request.newPost().apply {
            setURI(act.url)
            ask(rendering)
            options.contentFormat = FORMATS[act.contentType] ?: JSON
            options.addIfMatch(hexBytes(act.validator))
            setPayload(act.body)
        })

    private fun Request.ask(rendering: Rendering) {
        FORMATS[rendering.dialect.mediaType]?.let { options.accept = it }
        options.addOption(Option(ACCEPT_PROFILE, "<${rendering.dialect.profile}>"))
        rendering.rung.prefer?.let { options.addOption(Option(PREFER, it)) }
    }

    private fun send(request: Request): Outcome {
        val response = client.advanced(request)?.advanced()
            ?: return Outcome.Failed("no answer from ${request.uri}", null)
        return outcome(response)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val JSON = 50
        const val HEX = 16

        // SPEC §6.0.1: experimental options, one for one with the HTTP headers.
        private const val TEXT_MAX = 1034
        val ACCEPT_PROFILE = StringOptionDefinition(65_000, "Accept-Profile", false, 1, TEXT_MAX)
        val NO_CACHE = EmptyOptionDefinition(65_003, "No-Cache")
        val PREFER = StringOptionDefinition(65_004, "Prefer", false, 1, TEXT_MAX)
        val PROFILE = StringOptionDefinition(65_008, "Profile", false, 1, TEXT_MAX)
        val PREFERENCE_APPLIED = StringOptionDefinition(65_012, "Preference-Applied", false, 1, TEXT_MAX)
        val REGISTRY = MapBasedOptionRegistry(
            StandardOptionRegistry.getDefaultOptionRegistry(),
            ACCEPT_PROFILE, NO_CACHE, PREFER, PROFILE, PREFERENCE_APPLIED,
        )

        /** SPEC §6.0.1: experimental Content-Formats, and application/json's registered 50. */
        val FORMATS = mapOf(
            "application/hal+json" to 65_000,
            "application/schema+json" to 65_001,
            "application/problem+json" to 65_002,
            "application/json-home" to 65_003,
            "application/alps+json" to 65_004,
            "application/json" to JSON,
        )
        val MEDIA_TYPES = FORMATS.entries.associate { (type, format) -> format to type }

        fun outcome(response: Response): Outcome {
            val code = response.code
            val word = "%d.%02d".format(code.codeClass, code.codeDetail)
            val body = response.payloadString.orEmpty()
            val problem = ProblemDetails.type(body)
            return when {
                code.codeClass == SUCCESS_CLASS -> Outcome.Done(received(response), word)
                code == ResponseCode.PRECONDITION_FAILED -> Outcome.Stale(word, problem)
                code == ResponseCode.CONFLICT -> Outcome.NotOffered(word, problem)
                code == ResponseCode.UNPROCESSABLE_ENTITY ->
                    Outcome.Invalid(ProblemDetails.violations(body), word, problem)
                // CoAP has no 4.28: precondition required is 4.00 with its problem type.
                code == ResponseCode.BAD_REQUEST && problem == ProblemDetails.BASE + "precondition-required" ->
                    Outcome.PreconditionRequired(word, problem)
                else -> Outcome.Failed(word, problem)
            }
        }

        fun received(response: Response): Received {
            val options = response.options
            val applied = options.getOtherOption(PREFERENCE_APPLIED)?.stringValue
            return Received(
                bytes = response.payload ?: ByteArray(0),
                mediaType = if (options.hasContentFormat()) MEDIA_TYPES[options.contentFormat] else null,
                profiles = options.getOthers(PROFILE).map { it.stringValue },
                validator = options.eTags.firstOrNull()?.let(::hex),
                applied = Rung.applied(applied),
                appliedAs = applied,
            )
        }

        const val SUCCESS_CLASS = 2

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun hexBytes(text: String): ByteArray = text.chunked(2).map { it.toInt(HEX).toByte() }.toByteArray()
    }
}
