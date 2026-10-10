package steps

import org.eclipse.californium.core.CoapServer
import org.eclipse.californium.core.coap.CoAP.Code
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
import org.eclipse.californium.core.network.Exchange
import org.eclipse.californium.core.server.MessageDeliverer
import org.eclipse.californium.elements.config.Configuration
import org.eclipse.californium.elements.config.UdpConfig
import java.net.InetSocketAddress

/**
 * A scripted CoAP server on this machine for SPEC §6.0.1: one bed, in the
 * hal-schema-forms dialect, answering with the binding's options. It records
 * every request, so steps can check what ARTIE sent.
 */
class FakeCoapServer(var status: String) {
    val requests = mutableListOf<Request>()
    private var version = 1
    var rung: String? = null
    private var refusal: Pair<ResponseCode, String>? = null

    /** The 8-byte validator of the bed's current state. */
    val validator: ByteArray get() = byteArrayOf(0, 0, 0, 0, 0, 0, 0, version.toByte())

    private val server: CoapServer = run {
        CoapConfig.register()
        UdpConfig.register()
        val config = Configuration.createStandardWithoutFile()
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(config)
            .setInetSocketAddress(InetSocketAddress("localhost", 0))
            .setOptionRegistry(REGISTRY)
            .build()
        CoapServer(config).apply {
            addEndpoint(endpoint)
            setMessageDeliverer(Deliverer())
            start()
        }
    }

    val url: String get() = "coap://localhost:${server.endpoints.first().address.port}/beds/1"

    fun refuseNext(code: ResponseCode, problemType: String) {
        refusal = code to problemType
    }

    fun stop() = server.destroy()

    private fun answer(request: Request): Response {
        requests += request
        if (request.code == Code.GET) return representation(ResponseCode.CONTENT)
        val refused = refusal.also { refusal = null }
        return when {
            refused != null -> problem(refused.first, refused.second)
            request.options.ifMatch.none { it.contentEquals(validator) } -> Response(ResponseCode.PRECONDITION_FAILED)
            else -> {
                status = "PLANTED"
                version++
                representation(ResponseCode.CHANGED)
            }
        }
    }

    private fun problem(code: ResponseCode, type: String) = Response(code).apply {
        setPayload("""{"type": "https://github.com/jogoshugh/heart-rise/problems/$type", "title": "refused"}""")
        options.contentFormat = PROBLEM_JSON
    }

    private fun representation(code: ResponseCode) = Response(code).apply {
        setPayload(body())
        options.contentFormat = HAL_JSON
        options.maxAge = 0
        options.addETag(validator)
        options.addOption(Option(PROFILE, "https://github.com/jogoshugh/heart-rise/hal-schema-forms"))
        rung?.let { options.addOption(Option(PREFERENCE_APPLIED, it)) }
    }

    private fun body(): String {
        val form = if (status == "EMPTY") {
            """"sow": {"_links": {"target": {"href": "/beds/1/sow"}}, "method": "POST",
               "contentType": "application/json",
               "schema": {"type": "object", "properties": {"seedType": {"type": "string"}}}}"""
        } else {
            """"water": {"_links": {"target": {"href": "/beds/1/water"}}, "method": "POST",
               "contentType": "application/json",
               "schema": {"type": "object", "properties": {"milliliters": {"type": "integer"}}}}"""
        }
        return """{"status": "$status", "_links": {"self": {"href": "/beds/1"}}, "_forms": {$form}}"""
    }

    private inner class Deliverer : MessageDeliverer {
        override fun deliverRequest(exchange: Exchange) = exchange.sendResponse(answer(exchange.request))
        override fun deliverResponse(exchange: Exchange, response: Response) {
            exchange.request.response = response
        }
    }

    companion object {
        const val HAL_JSON = 65_000
        const val PROBLEM_JSON = 65_002
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
    }
}
