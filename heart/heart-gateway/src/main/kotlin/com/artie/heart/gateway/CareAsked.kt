package com.artie.heart.gateway

import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * Serves each request taking the care it names in `X-Gateway-Care` ([GatewayCare.HEADER]), or else the gateway's
 * own, [standing]. A request naming care the gateway doesn't know is refused, 400, before the upstream is asked.
 */
internal class CareAsked(private val standing: GatewayCare) : Filter {
    override fun invoke(next: HttpHandler): HttpHandler = { request ->
        val named = request.header(GatewayCare.HEADER)?.let { runCatching { GatewayCare.parse(it) } }
        val care = named?.getOrNull() ?: standing
        if (named?.isFailure == true) {
            Response(Status.BAD_REQUEST).header("Content-Type", "application/problem+json")
                .body(problem("${GatewayCare.HEADER} ${named.exceptionOrNull()?.message}"))
        } else {
            GatewayCare.during(care) { next(request) }
        }
    }

    private companion object {
        fun problem(detail: String) =
            """{"title":"Bad Request","status":400,"detail":"${detail.replace("\"", "'")}"}"""
    }
}
