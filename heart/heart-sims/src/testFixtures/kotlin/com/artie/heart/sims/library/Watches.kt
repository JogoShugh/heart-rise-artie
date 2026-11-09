package com.artie.heart.sims.library

/**
 * Ana's request to be told when a loan can be renewed: a resource of its own, brought into being by the library's
 * entry (201, its Location). It keeps the reply and the key the request named as links, so the library can answer
 * later. Nothing changes it yet, so its version is fixed.
 */
data class Watch(val id: Long, val loan: Long, val reply: String, val keys: String, val standing: String = AWAITING) {
    val version: Int get() = 1

    companion object {
        /** The watch's one standing, until the library answers it. */
        const val AWAITING = "awaiting"
    }
}
