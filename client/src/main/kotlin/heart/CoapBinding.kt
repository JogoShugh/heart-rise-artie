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
import org.eclipse.californium.scandium.DTLSConnector
import org.eclipse.californium.scandium.config.DtlsConfig
import org.eclipse.californium.scandium.config.DtlsConnectorConfig
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedSinglePskStore
import java.util.concurrent.ConcurrentHashMap

/**
 * The CoAP binding (SPEC §6.0.1), for `coap:` URIs, over UDP with Californium.
 * The requested rendering goes in the Accept option and in the experimental
 * options Accept-Profile and Prefer; the applied rendering comes back in
 * Profile and Preference-Applied. The 8-byte CoAP ETag is the validator,
 * written in hex for the core. It keeps no cache, so a Layer 1 read always
 * reaches the server, and it says No-Cache so no proxy answers it either.
 * With a [trace], every request carries the experimental option traceparent,
 * holding the value HTTP's traceparent header would, with a new span each.
 */
class CoapBinding(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val trace: Trace? = null,
    /** For coaps: the pre-shared key of each origin, held only inside its DTLS connector. */
    private val credentials: CredentialSource = CredentialSources.NONE,
) : Binding {

    private val client: CoapClient by lazy {
        CoapConfig.register()
        UdpConfig.register()
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(Configuration.createStandardWithoutFile())
            .setOptionRegistry(REGISTRY)
            .build()
        CoapClient().setEndpoint(endpoint).setTimeout(timeoutMs)
    }

    /** One DTLS client per coaps origin, holding that origin's key (RFC 7252 §9.1.3.1). */
    private val secureClients = ConcurrentHashMap<Origin, CoapClient>()

    private fun secureClient(origin: Origin, key: Credential.PreShared): CoapClient =
        secureClients.getOrPut(origin) {
            CoapConfig.register()
            UdpConfig.register()
            DtlsConfig.register()
            val config = Configuration.createStandardWithoutFile()
            val dtls = DtlsConnectorConfig.builder(config)
                .set(DtlsConfig.DTLS_ROLE, DtlsConfig.DtlsRole.CLIENT_ONLY)
                .set(DtlsConfig.DTLS_CIPHER_SUITES, listOf(CipherSuite.TLS_PSK_WITH_AES_128_GCM_SHA256))
                .setAdvancedPskStore(AdvancedSinglePskStore(key.identity, key.key()))
                .build()
            val endpoint = CoapEndpoint.Builder()
                .setConfiguration(config)
                .setConnector(DTLSConnector(dtls))
                .setOptionRegistry(REGISTRY)
                .build()
            CoapClient().setEndpoint(endpoint).setTimeout(timeoutMs)
        }

    override fun read(url: String, rendering: Rendering): Outcome =
        send(url, Request.newGet().apply {
            ask(rendering)
            options.addOption(Option(NO_CACHE))
        })

    override fun fetch(url: String, accept: List<String>, integrity: String?): Outcome =
        send(url, Request.newGet().apply {
            // CoAP's Accept takes one Content-Format; with several, the server picks.
            accept.mapNotNull { FORMATS[it] }.singleOrNull()?.let { options.accept = it }
        })

    override fun act(act: Act, rendering: Rendering): Outcome =
        send(act.url, Request.newPost().apply {
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

    /**
     * The span is kept under [url] as ARTIE was given it, so an event about the
     * request finds it: Californium rewrites the URI, dropping a default port.
     */
    private fun send(url: String, request: Request): Outcome {
        val origin = Origin.ofOrNull(url)
        val secure = origin?.scheme == "coaps"
        val key = if (secure) credentials.credentialFor(checkNotNull(origin)) as? Credential.PreShared else null
        if (secure && key == null) return Outcome.Unauthenticated(listOf(PSK), null, NO_KEY, null)
        request.setURI(url)
        trace?.let { trace ->
            val span = trace.newSpan(url)
            request.options.addOption(Option(TRACEPARENT, "00-${trace.traceId}-$span-01"))
        }
        val sender = if (key != null) secureClient(checkNotNull(origin), key) else client
        val response = sender.advanced(request)?.advanced()
        return when {
            response != null -> outcome(response)
            secure -> Outcome.Failed("no answer from ${request.uri}: the DTLS handshake did not complete", null)
            else -> Outcome.Failed("no answer from ${request.uri}", null)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val PSK = "PSK"

        /** Nothing was sent: there is no key for the origin, so no code came back. */
        const val NO_KEY = "no key"
        const val JSON = 50
        const val HEX = 16

        // SPEC §6.0.1: experimental options, one for one with the HTTP headers.
        private const val TEXT_MAX = 1034
        val ACCEPT_PROFILE = StringOptionDefinition(65_000, "Accept-Profile", false, 1, TEXT_MAX)
        val NO_CACHE = EmptyOptionDefinition(65_003, "No-Cache")
        val PREFER = StringOptionDefinition(65_004, "Prefer", false, 1, TEXT_MAX)
        val PROFILE = StringOptionDefinition(65_008, "Profile", false, 1, TEXT_MAX)
        val PREFERENCE_APPLIED = StringOptionDefinition(65_012, "Preference-Applied", false, 1, TEXT_MAX)

        /** Elective, safe to forward, and no part of the cache key (RFC 7252 §5.4.6): a trace never splits a cache. */
        val TRACEPARENT = StringOptionDefinition(65_020, "traceparent", true, 1, TEXT_MAX)
        val REGISTRY = MapBasedOptionRegistry(
            StandardOptionRegistry.getDefaultOptionRegistry(),
            ACCEPT_PROFILE, NO_CACHE, PREFER, PROFILE, PREFERENCE_APPLIED, TRACEPARENT,
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
