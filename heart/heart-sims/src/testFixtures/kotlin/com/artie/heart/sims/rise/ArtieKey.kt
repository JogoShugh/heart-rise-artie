package com.artie.heart.sims.rise

import com.artie.heart.sims.library.SigningKey

/** ARTIE's signing key: it signs ARTIE's requests to be told, and its reply service publishes it. */
object ArtieKey : SigningKey("artie-2026-11") {
    const val KEY_ID = "artie-2026-11"
}
