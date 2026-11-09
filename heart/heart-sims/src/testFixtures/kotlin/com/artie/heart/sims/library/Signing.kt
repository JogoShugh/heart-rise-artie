package com.artie.heart.sims.library

import com.artie.heart.Signer
import java.security.KeyPair
import java.security.KeyPairGenerator

/** The library's signing key (Ed25519, RFC 8037). Its public half is what Ana's reply checks a result against. */
object LibraryKey {
    const val KEY_ID = "library-2026-11"
    val pair: KeyPair by lazy { KeyPairGenerator.getInstance("Ed25519").generateKeyPair() }

    /** The library's signer: its requests are signed with [pair], under [KEY_ID]. */
    val signer: Signer by lazy { Signer(pair.private, KEY_ID) }

    /** A signer that claims the library's keyid with a key of its own: what a forged result is signed with. */
    val otherSigner: Signer by lazy { Signer(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().private, KEY_ID) }
}
