package com.artie.heart

/**
 * A plain list a server serves for its forms' choices (HAL-SCHEMA-FORMS-HR
 * Rule 8): at [path], [values] for the query it is asked with, under
 * [member]. A domain declares it, and a binding serves it like any Layer 1
 * resource, no-cache with an ETag, so the domain never touches the protocol.
 * The same [values] can back an option's server-side check
 * ([Options.fromLink]'s `resolve`).
 */
class Listing(val path: String, val member: String, val values: (query: Map<String, String>) -> List<Any?>) {
    /** The answer for [query]: the values under [member]. */
    fun body(query: Map<String, String>): Map<String, Any?> = mapOf(member to values(query))

    /** Names [body]'s bytes, so a client can revalidate it. */
    fun validator(body: Map<String, Any?>): String =
        "\"list-${ContentAddress.address(ContentAddress.canonical(body))}\""
}
