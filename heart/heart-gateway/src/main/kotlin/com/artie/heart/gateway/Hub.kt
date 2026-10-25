package com.artie.heart.gateway

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/**
 * The gateway as hub (spec/PLAN-LISTENERS.md, slice 6). A client that watches a resource reads it again with the
 * ETag it holds, and may prefer to wait (RFC 7240, `Prefer: wait=N`): while the answer would be 304, the read is
 * held, up to N seconds (at most [longest]), and read again when a webhook names what it reads, or every [recheck]
 * meanwhile, since a webhook may be lost. A webhook is taken at [PATH] only when its body is signed with [secret]
 * (`X-Hub-Signature-256`, an HMAC of SHA-256, as GitHub signs). It names resources by the API addresses in it
 * under [upstreamUrl]; each held read of one of them, of a list holding them, or of a list under one, wakes.
 */
class Hub(
    private val secret: String,
    private val upstreamUrl: String,
    private val mapper: ObjectMapper = ObjectMapper(),
    private val longest: Long = LONGEST,
    private val recheck: Long = RECHECK,
) {
    private val lock = ReentrantLock()
    private val woken = lock.newCondition()

    /** Each address a webhook named, by how many webhooks came before it: held reads look at those after theirs. */
    private val named = mutableListOf<String>()

    /** Reads held, and webhooks taken, before [handler]. */
    fun filter(): Filter = Filter { next ->
        { request ->
            when {
                request.method == Method.POST && request.uri.path == PATH -> webhook(request)
                waitFor(request) > 0 -> held(request, next)
                else -> next(request)
            }
        }
    }

    /** How long [request] prefers to wait, in seconds, at most [longest]; 0 when it doesn't, or can't be held. */
    private fun waitFor(request: Request): Long {
        val conditional = request.method == Method.GET && request.header("If-None-Match") != null
        val wait = request.header("Prefer").orEmpty().split(',').map { it.trim() }
            .firstOrNull { it.startsWith("wait=") }?.removePrefix("wait=")?.toLongOrNull()
        return if (conditional && wait != null) wait.coerceIn(0, longest) else 0
    }

    /** [request], read again each time what it reads may have changed, until it has, or its wait is over. */
    private fun held(request: Request, next: HttpHandler): Response {
        val wait = waitFor(request)
        val ends = System.nanoTime() + TimeUnit.SECONDS.toNanos(wait)
        val path = request.uri.path
        var seen = lock.withLock { named.size }
        var answer = next(request)
        while (answer.status == Status.NOT_MODIFIED && System.nanoTime() < ends) {
            seen = lock.withLock {
                var left = minOf(ends - System.nanoTime(), TimeUnit.SECONDS.toNanos(recheck))
                while (named.drop(seen).none { related(path, it) } && left > 0) left = woken.awaitNanos(left)
                named.size
            }
            answer = next(request)
        }
        // Beside what else the answer applied, the rung's forms and schema: one header, as RFC 7240 lists them.
        val applied = listOfNotNull(answer.header(APPLIED), "wait=$wait").joinToString(", ")
        return answer.replaceHeader(APPLIED, applied)
    }

    /** A webhook, taken only when signed with the secret: each address it names wakes the reads held of it. */
    private fun webhook(request: Request): Response {
        val body = request.bodyString().toByteArray()
        val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(secret.toByteArray(), HMAC)) }
        val expected = "sha256=" + mac.doFinal(body).joinToString("") { "%02x".format(it) }
        val signed = request.header(SIGNATURE).orEmpty()
        if (!MessageDigest.isEqual(expected.toByteArray(), signed.toByteArray())) return Response(Status.UNAUTHORIZED)
        val addresses = addresses(mapper.readTree(body))
        lock.withLock {
            named += addresses
            woken.signalAll()
        }
        return Response(Status.ACCEPTED)
    }

    /** Every API address under [upstreamUrl] the payload names, as the gateway serves it, but whole repositories. */
    private fun addresses(node: JsonNode): List<String> = when {
        node.isObject -> node.fields().asSequence().flatMap { (name, value) ->
            val own = value.takeIf { name == "url" && it.isTextual }?.asText()
                ?.takeIf { it.startsWith(upstreamUrl) }?.removePrefix(upstreamUrl)
            listOfNotNull(own?.takeIf { it.count { c -> c == '/' } > REPOSITORY_DEPTH }) + addresses(value)
        }.toList()
        node.isArray -> node.flatMap { addresses(it) }
        else -> emptyList()
    }

    /** Whether a change to [named] may change [path]: it is that resource, a list holding it, or one under it. */
    private fun related(path: String, named: String): Boolean =
        path == named || named.startsWith("$path/") || path.startsWith("$named/")

    companion object {
        /** Where GitHub's webhooks are taken. */
        const val PATH = "/hooks/github"
        private const val SIGNATURE = "X-Hub-Signature-256"
        private const val APPLIED = "Preference-Applied"
        private const val HMAC = "HmacSHA256"

        /** The longest a read is held, in seconds, whatever it prefers. */
        const val LONGEST = 60L

        /** How often a held read is read again meanwhile, in seconds, in case a webhook is lost. */
        const val RECHECK = 15L

        /** The slashes in a repository's own address, /repos/{owner}/{repo}: a webhook names one in every body. */
        private const val REPOSITORY_DEPTH = 3
    }
}
