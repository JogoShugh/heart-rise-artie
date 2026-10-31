package com.artie.heart.coap

import com.artie.heart.HeartResource
import com.artie.heart.Rung
import com.artie.heart.ServiceIndex
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * Serves a server's declared resource types (its [HeartResource] beans) over
 * CoAP. `heart.coap.port` sets the UDP port: 5683 by default, 0 for a free
 * one, -1 for none. The limits and the index are shared with any other
 * binding the server uses, so whichever auto-configuration runs first
 * defines them.
 */
// After the HTTP binding, when both serve one server: its service index, which links the
// OpenAPI description only HTTP serves, is the one both bindings share.
@AutoConfiguration(afterName = ["com.artie.heart.spring.HeartAutoConfiguration"])
class HeartCoapAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun servingLimits(
        @Value("\${heart.max-rung:L2}") maxRung: Rung,
        @Value("\${heart.page-size:${ServingLimits.DEFAULT_PAGE_SIZE}}") pageSize: Int,
    ): ServingLimits = ServingLimits(maxRung, pageSize)

    @Bean
    @ConditionalOnMissingBean
    fun serviceIndex(resources: List<HeartResource<*, *>>, @Value("\${heart.title:}") title: String): ServiceIndex =
        ServiceIndex(resources.map { it.layers.type }, title.ifBlank { null })

    @Bean
    fun coapFrontDoor(
        resources: List<HeartResource<*, *>>,
        index: ServiceIndex,
        limits: ServingLimits,
        // The application's own Jackson set-up when it has one; a CoAP-only server may not.
        mapper: ObjectProvider<ObjectMapper>,
        listeners: CoapListeners,
    ): CoapFrontDoor = CoapFrontDoor(
        resources, index, limits, mapper.getIfAvailable { ObjectMapper().findAndRegisterModules() },
        listeners.port, listeners.secure,
    )

    /**
     * Where CoAP listens: plain CoAP on `heart.coap.port`, loopback only; coaps
     * off unless both `heart.coaps.port` and `heart.coaps.psk` (HEART_COAPS_PORT,
     * HEART_COAPS_PSK) are set, and one without the other stops the server.
     */
    @Bean
    fun coapListeners(
        @Value("\${heart.coap.port:5683}") port: Int,
        @Value("\${heart.coaps.port:-1}") securePort: Int,
        @Value("\${heart.coaps.psk:}") keys: String,
    ): CoapListeners = CoapListeners(port, CoapsSettings.parse(securePort, keys))
}
