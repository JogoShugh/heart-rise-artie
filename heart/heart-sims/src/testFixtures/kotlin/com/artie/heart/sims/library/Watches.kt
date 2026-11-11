package com.artie.heart.sims.library

/**
 * Ana's request to be told when a loan can be renewed: a resource of its own, brought into being by the library's
 * entry (201, its Location). It keeps the reply and the key the request named, so the library can answer later.
 * It awaits until the library answers it, or the caller cancels it; each change takes the next version.
 */
data class Watch(
    val id: Long,
    val loan: Long,
    val reply: String,
    val keys: String,
    val standing: String = AWAITING,
    val version: Int = 1,
) {
    companion object {
        /** The library has not answered yet: the caller may still cancel. */
        const val AWAITING = "awaiting"

        /** The library has sent its one answer, a result or a failure. */
        const val ANSWERED = "answered"

        /** The caller withdrew the request before it was answered. */
        const val CANCELLED = "cancelled"
    }
}
