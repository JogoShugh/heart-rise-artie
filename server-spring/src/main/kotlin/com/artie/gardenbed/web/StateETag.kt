package com.artie.gardenbed.web

/**
 * SPEC §6.1 and §6.2 `[0.5]`: a Layer 1 ETag has two parts. The state
 * version is the bed's last event sequence number, the same number the
 * event store's UNIQUE constraint enforces. The variant tag names the
 * dialect and rung, so each representation of one state has its own
 * strong validator: `"seq-5.hsf.L2"`. Clients treat the value as opaque;
 * only this server reads the parts back.
 */
object StateETag {
    private val issued = Regex("^\"seq-(\\d+)\\.[a-z]+\\.L\\d\"$")

    fun of(stateVersion: Int, dialectTag: String, rung: Rung): String =
        "\"seq-$stateVersion.$dialectTag.${rung.name}\""

    /**
     * True when any entity-tag in an If-Match value was issued by this
     * server for [stateVersion], whatever its dialect or rung (the [0.5]
     * precondition scope). Weak tags (`W/"..."`) never match: If-Match
     * uses strong comparison (RFC 9110 §13.1.1).
     */
    fun matchesStateVersion(ifMatch: String, stateVersion: Int): Boolean =
        ifMatch.split(',')
            .mapNotNull { issued.matchEntire(it.trim())?.groupValues?.get(1)?.toInt() }
            .any { it == stateVersion }
}
