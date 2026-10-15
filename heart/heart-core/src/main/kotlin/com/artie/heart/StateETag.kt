package com.artie.heart

/**
 * SPEC §6.1 and §6.2: a Layer 1 ETag names one representation of one state:
 * the resource's state version (for an event-sourced resource, its last event
 * sequence number), the dialect and the rung, then the digest of the
 * representation itself (the first 16 hex digits of the SHA-256 of its
 * canonical JSON), as in `"seq-5.hsf.L2.e8e98768123683fa"`.
 *
 * The digest is what makes it strong, as RFC 9110 §8.8.1 requires: when a
 * server release renders an unchanged state differently (a renamed member,
 * another form), the validator changes too, so no cache revalidates the old
 * representation as current. Clients treat the value as opaque.
 */
object StateETag {

    fun of(stateVersion: Int, dialectTag: String, rung: Rung, representation: Any?): String {
        val digest = ContentAddress.address(ContentAddress.canonical(representation))
        return "\"seq-$stateVersion.$dialectTag.${rung.name}.$digest\""
    }

    /**
     * RFC 9110 §13.1.1: `*` matches any current representation; otherwise
     * some listed entity-tag must equal [current] by strong comparison,
     * character for character. A weak tag (`W/"..."`) never equals a strong one.
     */
    fun matches(ifMatch: String, current: String): Boolean =
        ifMatch.trim() == "*" || ifMatch.split(',').any { it.trim() == current }
}
