package com.artie.heart

/**
 * SPEC §6.1 and §6.2: a Layer 1 ETag names one representation of one state:
 * the resource's state version (for an event-sourced resource, its last event
 * sequence number), the dialect, and the rung, as in `"seq-5.hsf.L2"`.
 * Each representation of a state has its own strong validator, as RFC 9110
 * §8.8.1 requires. Clients treat the value as opaque.
 */
object StateETag {

    fun of(stateVersion: Int, dialectTag: String, rung: Rung): String =
        "\"seq-$stateVersion.$dialectTag.${rung.name}\""

    /**
     * RFC 9110 §13.1.1: `*` matches any current representation; otherwise
     * some listed entity-tag must equal [current] by strong comparison,
     * character for character. A weak tag (`W/"..."`) never equals a strong one.
     */
    fun matches(ifMatch: String, current: String): Boolean =
        ifMatch.trim() == "*" || ifMatch.split(',').any { it.trim() == current }
}
