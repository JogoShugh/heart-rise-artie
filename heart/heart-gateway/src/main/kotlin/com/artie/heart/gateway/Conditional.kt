package com.artie.heart.gateway

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.security.MessageDigest
import java.util.Base64

/**
 * The conditional rung (spec/ideas/gateway-pass-through.md): conditional writes the upstream doesn't have, for a
 * client passing through. A read is answered with the gateway's own ETag, a digest of the upstream's answer, and
 * an If-None-Match that matches it is answered 304 by the gateway. A write that replaces a resource the upstream
 * also reads ([readable]: PATCH, PUT or DELETE at its address) must carry that ETag: the gateway reads the resource
 * again, as the caller, and refuses a write with none (428) or with one no longer current (412), before the
 * upstream is asked. A write that adds to a resource (a POST) is not held to it.
 */
internal class Conditional(private val upstream: HttpHandler, private val readable: (String) -> Boolean) {

    fun exchange(request: Request): Response = when {
        request.method == Method.GET -> read(request)
        request.method in REPLACING && readable(request.uri.path) -> write(request)
        else -> upstream(request)
    }

    private fun read(request: Request): Response {
        val asked = request.header(IF_NONE_MATCH)
        val answer = upstream(request.removeHeader(IF_NONE_MATCH))
        if (answer.status != Status.OK) return answer
        val text = answer.bodyString()
        val etag = etagOf(text)
        return if (asked == etag) {
            Response(Status.NOT_MODIFIED).header(ETAG, etag)
        } else {
            answer.body(text).replaceHeader(ETAG, etag)
        }
    }

    private fun write(request: Request): Response {
        val sent = request.header(IF_MATCH)
        return when {
            sent == null -> damRefusal(Status.PRECONDITION_REQUIRED, NO_VALIDATOR)
            current(request) != sent -> damRefusal(Status.PRECONDITION_FAILED, STALE)
            else -> upstream(request.removeHeader(IF_MATCH))
        }
    }

    /** The gateway's ETag for the resource [request] writes, read again now as its caller; null if unreadable. */
    private fun current(request: Request): String? {
        val again = READ_AS.fold(Request(Method.GET, request.uri)) { read, name ->
            request.header(name)?.let { read.header(name, it) } ?: read
        }
        return upstream(again).takeIf { it.status == Status.OK }?.let { etagOf(it.bodyString()) }
    }

    private companion object {
        const val ETAG = "ETag"
        const val IF_MATCH = "If-Match"
        const val IF_NONE_MATCH = "If-None-Match"
        const val DIGEST_CHARS = 22
        const val NO_VALIDATOR =
            "this write replaces what is there: send If-Match with the ETag of your last read of it"
        const val STALE = "the resource changed since it was read; read it again and decide again"
        val REPLACING = setOf(Method.PATCH, Method.PUT, Method.DELETE)

        /** What a read again carries from the write: whose it is, and the shape it asks for. */
        val READ_AS = listOf("Authorization", "Accept")

        /** The gateway's own ETag for an answer: a digest of its body, strong, as the gateway read it. */
        fun etagOf(body: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            return "\"gw-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).take(DIGEST_CHARS) + "\""
        }
    }
}
