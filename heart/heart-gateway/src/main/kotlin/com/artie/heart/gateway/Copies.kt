package com.artie.heart.gateway

import java.security.MessageDigest
import java.util.HexFormat

/** An upstream answer as last read, with the ETag it came with. */
class Copy(val etag: String, val answer: Answer)

/**
 * What each caller last read from the upstream, by caller and path, with its
 * ETag, so reading it again can be a conditional request: GitHub answers a
 * 304 for free under its rate limits. A caller is known by the digest of its
 * credential, never the credential itself, and one caller's copy never
 * answers another, since what each may see differs. The least recently used
 * copies go first, past [capacity].
 */
internal class Copies(private val capacity: Int = DEFAULT_CAPACITY) {
    private val held = object : LinkedHashMap<String, Copy>(INITIAL, LOAD, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Copy>?) = size > capacity
    }

    fun held(authorization: String?, path: String): Copy? = synchronized(held) { held[key(authorization, path)] }

    fun keep(authorization: String?, path: String, etag: String, answer: Answer) {
        synchronized(held) { held[key(authorization, path)] = Copy(etag, answer) }
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
    }
}
