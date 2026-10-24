package com.artie.heart.coap

import java.util.Base64

/** Where a server's CoAP front door listens: plain CoAP on [port] (-1 for none), and coaps when [secure] is set. */
class CoapListeners(val port: Int, val secure: CoapsSettings?)

/**
 * The coaps endpoint's settings: its UDP port and the pre-shared key of each
 * client identity it admits (DTLS 1.2, RFC 7252 §9.1.3.1). A key is held as
 * itself, not as a hash: DTLS needs the key to run the handshake.
 */
class CoapsSettings private constructor(val port: Int, val keys: Map<String, ByteArray>) {

    companion object {
        private const val MIN_KEY_BYTES = 16

        /**
         * Reads `heart.coaps.port` and `HEART_COAPS_PSK` (`identity:base64-key,...`).
         * Both unset means no coaps endpoint. One without the other stops the
         * server at the start, with a message that names no key.
         */
        fun parse(port: Int, keys: String): CoapsSettings? {
            val entries = keys.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            check(port >= 0 || entries.isEmpty()) { "HEART_COAPS_PSK is set, but heart.coaps.port is not" }
            if (port < 0) return null
            check(entries.isNotEmpty()) { "coaps needs HEART_COAPS_PSK (identity:base64-key,...)" }
            return CoapsSettings(port, entries.associate(::entry))
        }

        private fun entry(text: String): Pair<String, ByteArray> {
            check(':' in text) { "expected identity:base64-key in HEART_COAPS_PSK" }
            val identity = text.substringBefore(':')
            val key = runCatching { Base64.getDecoder().decode(text.substringAfter(':')) }
                .getOrElse { error("the key for $identity is not base64") }
            check(key.size >= MIN_KEY_BYTES) { "the key for $identity is shorter than $MIN_KEY_BYTES bytes" }
            return identity to key
        }
    }
}
