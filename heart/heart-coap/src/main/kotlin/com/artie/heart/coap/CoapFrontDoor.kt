package com.artie.heart.coap

import com.artie.heart.HeartResource
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.eclipse.californium.core.CoapServer
import org.eclipse.californium.core.coap.Response
import org.eclipse.californium.core.config.CoapConfig
import org.eclipse.californium.core.network.CoapEndpoint
import org.eclipse.californium.core.network.Exchange
import org.eclipse.californium.core.server.MessageDeliverer
import org.eclipse.californium.elements.config.Configuration
import org.eclipse.californium.elements.config.UdpConfig
import org.springframework.context.SmartLifecycle
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A server's CoAP front door (SPEC §6.0.1): a Californium server on UDP,
 * beside the HTTP one, answering from the same declarations. [configuredPort]
 * is the port (5683, CoAP's default); 0 picks a free one; -1 turns it off.
 */
class CoapFrontDoor(
    resources: List<HeartResource<*, *>>,
    index: ServiceIndex,
    limits: ServingLimits,
    mapper: ObjectMapper,
    private val configuredPort: Int,
) : SmartLifecycle {
    private val routes = CoapRoutes(resources, index, limits, mapper)
    private var server: CoapServer? = null

    /** The UDP address the server listens on, once started. */
    val address: InetSocketAddress get() = checkNotNull(server).endpoints.first().address

    /** The UDP port the server listens on, once started. */
    val port: Int get() = address.port

    override fun start() {
        if (configuredPort < 0) return
        CoapConfig.register()
        UdpConfig.register()
        val config = Configuration.createStandardWithoutFile()
            .set(CoapConfig.MAX_RESOURCE_BODY_SIZE, MAX_BODY)
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(config)
            // CoAP has no Authorization header, so no Basic or Bearer credential can be
            // checked here: only the same machine may reach it (spec/ideas/coap-security.md).
            .setInetSocketAddress(InetSocketAddress(InetAddress.getLoopbackAddress(), configuredPort))
            .setOptionRegistry(CoapOptions.registry)
            .build()
        server = CoapServer(config).apply {
            addEndpoint(endpoint)
            setMessageDeliverer(Deliverer())
            start()
        }
    }

    override fun stop() {
        server?.destroy()
        server = null
    }

    override fun isRunning(): Boolean = server != null

    /** Every request goes to [CoapRoutes]; Californium has already reassembled block-wise bodies. */
    private inner class Deliverer : MessageDeliverer {
        override fun deliverRequest(exchange: Exchange) {
            val response: Response = runBlocking { routes.handle(exchange.request) }
            exchange.sendResponse(response)
        }

        override fun deliverResponse(exchange: Exchange, response: Response) {
            exchange.request.response = response
        }
    }

    private companion object {
        const val MAX_BODY = 65_536
    }
}
