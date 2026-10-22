package com.artie.heart.gateway

import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

/** An upstream answer as last read, with the ETag it came with, and when the upstream last said it was so. */
class Copy(val etag: String, val answer: Answer, val at: Instant)

/**
 * What each caller last read from the upstream, by caller and path, with its
 * ETag, so reading it again can be a conditional request: GitHub answers a
 * 304 for free under its rate limits. A caller is known by the digest of its
 * credential, never the credential itself, and one caller's copy never
 * answers another, since what each may see differs. The least recently used
 * copies go first, past [capacity]. A write drops the caller's copies of what
 * it touched and of what lies above or below it, so a copy trusted without
 * asking (GatewayCare.trustCopies) is never one the caller's own act made stale.
 */
internal class Copies(private val capacity: Int = DEFAULT_CAPACITY, private val clock: Clock = Clock.systemUTC()) {
    private val held = object : LinkedHashMap<String, Copy>(INITIAL, LOAD, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Copy>?) = size > capacity
    }

    fun held(authorization: String?, path: String): Copy? = synchronized(held) { held[key(authorization, path)] }

    /** The copy of [path], when the upstream said it was so no longer than [within] ago. */
    fun fresh(authorization: String?, path: String, within: Duration): Copy? =
        held(authorization, path)?.takeIf { !it.at.plus(within).isBefore(clock.instant()) }

    fun keep(authorization: String?, path: String, etag: String, answer: Answer) {
        synchronized(held) { held[key(authorization, path)] = Copy(etag, answer, clock.instant()) }
    }

    /** Drops the caller's copies of [written], and of every path above or below it, queries aside. */
    fun drop(authorization: String?, written: String) {
        val caller = key(authorization, "")
        synchronized(held) {
            held.keys.removeIf { key ->
                val path = key.removePrefix(caller).substringBefore('?')
                key.startsWith(caller) && (within(path, written) || within(written, path))
            }
        }
    }

    private fun key(authorization: String?, path: String): String {
        val caller = authorization?.let {
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.orEmpty()
        return "$caller $path"
    }

    private companion object {
        const val DEFAULT_CAPACITY = 1000
        const val INITIAL = 16
        const val LOAD = 0.75f

        /** Whether [path] is [above], or lies below it. */
        fun within(path: String, above: String) = path == above || path.startsWith(above.trimEnd('/') + "/")
    }
}
