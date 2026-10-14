package com.artie.gardenbed.config

import com.artie.gardenbed.service.GardenWeather
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The garden's operator reports a hard frost, only in a garden with frost:
 * `POST /actuator/frost` freezes every planted bed and lists the beds it froze.
 * It is weather, not a client's act, so no form offers it and the OpenAPI
 * description does not list it. Actuator serves it, so the garden touches no
 * HTTP, and it needs a credential like every other route.
 */
@Component
@Endpoint(id = "frost")
@ConditionalOnProperty("garden.frost.enabled", havingValue = "true")
class FrostEndpoint(private val weather: GardenWeather) {
    @WriteOperation
    fun frost(): Mono<Map<String, List<Long>>> = mono { mapOf("frozen" to weather.frost().map { it.id }) }
}
