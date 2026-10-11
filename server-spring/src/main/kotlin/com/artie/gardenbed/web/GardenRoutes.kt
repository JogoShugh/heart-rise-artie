package com.artie.gardenbed.web

import com.artie.gardenbed.domain.GardenBedState
import com.artie.gardenbed.domain.GardenBedStatus
import com.artie.gardenbed.service.GardenBedService
import com.artie.heart.HeartResource
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.spring.HeartHandlers
import com.artie.heart.spring.HttpRefusals
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import java.time.Instant
import java.time.format.DateTimeParseException

class MalformedAsOfException(message: String) : RuntimeException(message)

/**
 * What the garden adds to HEART, because its beds are event-sourced: a bed's
 * state as of any past instant, and its raw event log. heart-spring serves
 * the bed itself; a past state is rendered by heart-spring too, so it reads
 * exactly like the current one.
 *
 * Example: `GET /beds/2?asOf=2026-07-05T00:00:00Z`.
 */
@Configuration
class GardenRoutes {

    @Bean
    fun gardenHistoryRoutes(
        service: GardenBedService,
        beds: HeartResource<GardenBedState, GardenBedStatus>,
        handlers: HeartHandlers,
    ): RouterFunction<ServerResponse> = coRouter {
        GET("/beds/{id}", queryParam("asOf") { true }) { request ->
            val asOf = request.queryParam("asOf").orElseThrow()
            val instant = try {
                Instant.parse(asOf)
            } catch (_: DateTimeParseException) {
                throw MalformedAsOfException("'$asOf' is not a valid ISO-8601 instant, e.g. 2026-07-05T00:00:00Z")
            }
            handlers.of(beds).represent(service.stateAt(id(request), instant), request)
        }

        /** The raw event log of a bed: what happened, in order, whatever the dialect. */
        GET("/beds/{id}/history") { request ->
            val body = service.history(id(request)).map { event ->
                mapOf(
                    "sequenceNo" to event.sequenceNo,
                    "type" to event::class.simpleName,
                    "occurredAt" to event.occurredAt.toString(),
                    "event" to event,
                )
            }
            ServerResponse.ok().bodyValueAndAwait(body)
        }

        onError<MalformedAsOfException> { failure, _ ->
            ServerResponse.badRequest().bodyValueAndAwait(mapOf("error" to (failure.message ?: "malformed asOf")))
        }
        onError<Throwable> { failure, _ -> HttpRefusals.of(failure) ?: throw failure }
    }

    private fun id(request: ServerRequest): Long =
        request.pathVariable("id").toLongOrNull()
            ?: throw ResourceNotFoundException("No garden bed with id ${request.pathVariable("id")}")
}
