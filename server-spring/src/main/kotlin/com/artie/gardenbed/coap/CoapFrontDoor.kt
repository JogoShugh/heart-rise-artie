package com.artie.gardenbed.coap

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.web.BedActs
import com.artie.heart.HeartResource
import com.artie.heart.ServiceIndex
import com.artie.heart.spring.ServingLimits
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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.net.InetSocketAddress

/**
 * The server's CoAP front door (SPEC §6.0.1): a Californium server on UDP,
 * beside the HTTP one, answering from the same services. `heart.coap.port`
 * sets the port (5683, CoAP's default); 0 picks a free one; -1 turns it off.
 */
@Component
class CoapFrontDoor(
    beds: HeartResource<GardenBedState, GardenBedStatus>,
    acts: BedActs,
    index: ServiceIndex,
    limits: ServingLimits,
    objectMapper: ObjectMapper,
    @Value("\${heart.coap.port:5683}") private val configuredPort: Int,
) : SmartLifecycle {
    private val routes = CoapRoutes(beds, acts, index, limits, objectMapper)
    private var server: CoapServer? = null

    /** The UDP port the server listens on, once started. */
    val port: Int get() = checkNotNull(server).endpoints.first().address.port

    override fun start() {
        if (configuredPort < 0) return
        CoapConfig.register()
        UdpConfig.register()
        val config = Configuration.createStandardWithoutFile()
            .set(CoapConfig.MAX_RESOURCE_BODY_SIZE, MAX_BODY)
        val endpoint = CoapEndpoint.Builder()
            .setConfiguration(config)
            .setInetSocketAddress(InetSocketAddress(configuredPort))
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
