package com.artie.heart.sims

import java.security.MessageDigest
import java.util.HexFormat

/** Bearer tokens as a HEART service is configured with them: as their SHA-256, in hex. */
object Tokens {
    fun sha256(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
