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
import org.eclipse.californium.scandium.DTLSConnector
import org.eclipse.californium.scandium.config.DtlsConfig
import org.eclipse.californium.scandium.config.DtlsConnectorConfig
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite
import org.eclipse.californium.scandium.dtls.pskstore.AdvancedMultiPskStore
import org.springframework.context.SmartLifecycle
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A server's CoAP front door (SPEC §6.0.1): a Californium server on UDP,
 * beside the HTTP one, answering from the same declarations. [configuredPort]
 * is the port (5683, CoAP's default); 0 picks a free one; -1 turns it off.
 *
 * Plain CoAP carries no credential, so it listens on the loopback address only.
 * With [secure] settings, a coaps endpoint (DTLS 1.2 with a pre-shared key per
 * client) listens on every address, and admits only the identities it holds a
 * key for: a client without one never completes the handshake.
 */
class CoapFrontDoor(
    resources: List<HeartResource<*, *>>,
    index: ServiceIndex,
    limits: ServingLimits,
    mapper: ObjectMapper,
    private val configuredPort: Int,
    private val secure: CoapsSettings? = null,
) : SmartLifecycle {
    private val routes = CoapRoutes(resources, index, limits, mapper)
    private var server: CoapServer? = null
    private var plainEndpoint: CoapEndpoint? = null
    private var secureEndpoint: CoapEndpoint? = null

    /** The plain endpoint's UDP address, once started. */
    val address: InetSocketAddress get() = checkNotNull(plainEndpoint).address

    /** The plain endpoint's UDP port, once started. */
    val port: Int get() = address.port

    /** The coaps endpoint's UDP address, once started with coaps settings. */
    val secureAddress: InetSocketAddress get() = checkNotNull(secureEndpoint) { "coaps is off" }.address

    /** The coaps endpoint's UDP port, once started with coaps settings. */
    val securePort: Int get() = secureAddress.port

    override fun start() {
        if (configuredPort < 0 && secure == null) return
        CoapConfig.register()
        UdpConfig.register()
        DtlsConfig.register()
        val config = Configuration.createStandardWithoutFile()
            .set(CoapConfig.MAX_RESOURCE_BODY_SIZE, MAX_BODY)
        if (configuredPort >= 0) {
            plainEndpoint = CoapEndpoint.Builder()
                .setConfiguration(config)
                // Plain CoAP has no credential at all: only the same machine may reach it.
                .setInetSocketAddress(InetSocketAddress(InetAddress.getLoopbackAddress(), configuredPort))
                .setOptionRegistry(CoapOptions.registry)
                .build()
        }
        secure?.let { secureEndpoint = secureEndpoint(config, it) }
        server = CoapServer(config).apply {
            listOfNotNull(plainEndpoint, secureEndpoint).forEach(::addEndpoint)
            setMessageDeliverer(Deliverer())
            start()
        }
    }

    private fun secureEndpoint(config: Configuration, settings: CoapsSettings): CoapEndpoint {
        val keys = AdvancedMultiPskStore().apply { settings.keys.forEach { (identity, key) -> setKey(identity, key) } }
        val dtls = DtlsConnectorConfig.builder(config)
            .set(DtlsConfig.DTLS_ROLE, DtlsConfig.DtlsRole.SERVER_ONLY)
            .set(DtlsConfig.DTLS_CIPHER_SUITES, listOf(CipherSuite.TLS_PSK_WITH_AES_128_GCM_SHA256))
            .setAddress(InetSocketAddress(settings.port))
            .setAdvancedPskStore(keys)
            .build()
        return CoapEndpoint.Builder()
            .setConfiguration(config)
            .setConnector(DTLSConnector(dtls))
            .setOptionRegistry(CoapOptions.registry)
            .build()
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
