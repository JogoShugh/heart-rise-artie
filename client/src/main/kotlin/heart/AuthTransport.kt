package heart

import java.net.URI

/**
 * Adds the credential for a request's origin as `Authorization`, just above
 * the wire and below the client's cache, so the cache never holds it and
 * nothing above it (the core, the event log, a reasoner) ever sees it.
 *
 * - Only the request's own origin gets its credential. A representation
 *   chooses where its links lead, so a link to another origin gets nothing.
 * - A credential is never sent over plain HTTP beyond the machine.
 * - An address carrying a user name or password is refused before anything is sent.
 * - A request that already carries Authorization keeps it.
 * - After a 401, the source is asked once whether the credential has changed;
 *   only a changed one is sent, once.
 *
 * The response records which scheme was sent ([HeartResponse.credentialSent]),
 * so a refusal can say so without the credential.
 */
class AuthTransport(private val credentials: CredentialSource, private val next: Transport) : Transport {

    override fun send(request: HeartRequest): HeartResponse {
        require(runCatching { URI(request.url).rawUserInfo }.getOrNull() == null) {
            "a request carries a user name or password in its address; it was refused, and nothing was sent"
        }
        val origin = Origin.ofOrNull(request.url)?.takeIf { request.header(AUTHORIZATION) == null }
        val credential = origin?.let(credentials::credentialFor)
        return if (origin == null || credential == null) next.send(request) else send(request, origin, credential)
    }

    private fun send(request: HeartRequest, origin: Origin, credential: Credential): HeartResponse {
        require(origin.scheme != "http" || origin.loopback) {
            "ARTIE will not send a credential over plain HTTP to $origin; nothing was sent"
        }
        val response = sendWith(request, credential)
        val renewed = if (response.status == UNAUTHORIZED) credentials.renewed(origin) else null
        return if (renewed == null || renewed == credential) response else sendWith(request, renewed)
    }

    private fun sendWith(request: HeartRequest, credential: Credential): HeartResponse =
        next.send(request.copy(headers = request.headers + (AUTHORIZATION to credential.header())))
            .sentWith(credential.scheme)

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val UNAUTHORIZED = 401
    }
}
