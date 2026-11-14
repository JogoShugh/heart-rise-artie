package heart

/**
 * Names the client in `User-Agent` (RFC 9110 §10.1.5) on every request, so a
 * server or a filter in front of it can tell clients apart. A request that
 * already carries a User-Agent keeps it. Sits outermost, above the client's
 * cache, so two clients behind one shared cache keep their own names.
 */
class UserAgentTransport(private val agent: String = DEFAULT, private val next: Transport) : Transport {

    override fun send(request: HeartRequest): HeartResponse =
        next.send(
            if (request.header(USER_AGENT) != null) request
            else request.copy(headers = request.headers + (USER_AGENT to agent)),
        )

    companion object {
        const val DEFAULT = "ARTIE/0.5"
        private const val USER_AGENT = "User-Agent"
    }
}
