package com.artie.gardenbed.coap

import org.eclipse.californium.core.coap.option.EmptyOptionDefinition
import org.eclipse.californium.core.coap.option.MapBasedOptionRegistry
import org.eclipse.californium.core.coap.option.OptionRegistry
import org.eclipse.californium.core.coap.option.StandardOptionRegistry
import org.eclipse.californium.core.coap.option.StringOptionDefinition
import java.security.MessageDigest

/**
 * SPEC §6.0.1: what the CoAP binding adds to CoAP. The options come from the
 * experimental range (RFC 7252 §12.2) and stand one for one for HTTP headers;
 * so do the Content-Formats for media types CoAP has no number for.
 */
object CoapOptions {
    const val ACCEPT_PROFILE_NUMBER = 65_000
    const val NO_CACHE_NUMBER = 65_003
    const val PREFER_NUMBER = 65_004
    const val PROFILE_NUMBER = 65_008
    const val PREFERENCE_APPLIED_NUMBER = 65_012

    /** Elective, safe to forward, part of the cache key. */
    val ACCEPT_PROFILE = text(ACCEPT_PROFILE_NUMBER, "Accept-Profile")

    /** Critical and unsafe to forward: a proxy that does not know it must refuse, never answer from its cache. */
    val NO_CACHE = EmptyOptionDefinition(NO_CACHE_NUMBER, "No-Cache")

    val PREFER = text(PREFER_NUMBER, "Prefer")
    val PROFILE = text(PROFILE_NUMBER, "Profile")
    val PREFERENCE_APPLIED = text(PREFERENCE_APPLIED_NUMBER, "Preference-Applied")

    private fun text(number: Int, name: String) = StringOptionDefinition(number, name, false, 1, MAX_TEXT)

    const val HAL_JSON = 65_000
    const val SCHEMA_JSON = 65_001
    const val PROBLEM_JSON = 65_002
    const val JSON_HOME = 65_003
    const val ALPS_JSON = 65_004
    const val JSON = 50

    /** CoAP's standard options and these. */
    val registry: OptionRegistry = MapBasedOptionRegistry(
        StandardOptionRegistry.getDefaultOptionRegistry(),
        ACCEPT_PROFILE, NO_CACHE, PREFER, PROFILE, PREFERENCE_APPLIED,
    )

    /** A CoAP ETag holds at most 8 bytes: the first 8 of SHA-256 over the HTTP binding's ETag value. */
    fun validator(httpETag: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(httpETag.toByteArray(Charsets.UTF_8)).copyOf(ETAG_BYTES)

    private const val ETAG_BYTES = 8
    private const val MAX_TEXT = 1034
}
