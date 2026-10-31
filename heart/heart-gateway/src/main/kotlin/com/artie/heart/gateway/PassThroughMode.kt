package com.artie.heart.gateway

import com.artie.heart.Caller
import com.artie.heart.http4k.Credentials
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * The transparent rung of spec/ideas/gateway-pass-through.md: a request carrying `X-Gateway-Mode: pass-through` goes
 * to the [upstream] as it came, called as [auth] says (as the caller, or as the gateway), and the upstream's answer
 * comes back as it is. Nothing is checked or added, so a plain client crosses the same layer a HEART client does.
 * A request with no credential the gateway admits ([credentials]) is refused here, as any other; every request
 * without the header is served as HEART, at the same addresses. (Not [Passthrough], which serves reads a connector
 * lists but doesn't model, as HEART.)
 */
internal class PassThroughMode(
    private val upstream: HttpHandler,
    private val auth: UpstreamAuth,
    private val credentials: Credentials,
) : Filter {
    override fun invoke(next: HttpHandler): HttpHandler = { request ->
        if (request.header(MODE) != PASS_THROUGH) next(request) else forward(request)
    }

    private fun forward(request: Request): Response {
        val caller = request.header("Authorization")
        if (!credentials.admits(caller)) {
            return Response(Status.UNAUTHORIZED).header("WWW-Authenticate", "Bearer")
        }
        val credential = Caller.during(caller) { auth.authorization() }
        val sent = request.removeHeader(MODE).removeHeader("Host").removeHeader("Authorization")
            .let { plain -> credential?.let { plain.header("Authorization", it) } ?: plain }
        return HOP_BY_HOP.fold(upstream(sent)) { answer, header -> answer.removeHeader(header) }
    }

    companion object {
        /** The header a caller asks by, and the value that asks. */
        const val MODE = "X-Gateway-Mode"
        const val PASS_THROUGH = "pass-through"

        /** Headers about one connection, never forwarded (RFC 9110 §7.6.1). */
        private val HOP_BY_HOP = listOf("Connection", "Keep-Alive", "Transfer-Encoding", "Upgrade")
    }
}
