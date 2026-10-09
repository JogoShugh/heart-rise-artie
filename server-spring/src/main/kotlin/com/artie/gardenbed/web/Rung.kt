package com.artie.gardenbed.web

/** The two Layer 2 renderings (SPEC §6.5): different bytes, so different addresses. */
enum class SetRendering {
    /** L1: the set carries each schema body inline. */
    EMBEDDED_SCHEMAS,

    /** L2: the set carries a `$ref` to each schema. */
    SCHEMA_POINTERS,
}

/**
 * SPEC §6.4 / §6.10.1-6.10.3: the rendering rung a client asks for with
 * `Prefer` tokens that ask for a layer by link. No token means L0,
 * everything collapsed into Layer 1. Any token the server does not honour
 * is ignored (RFC 7240), so an unknown or partial request, including the
 * withdrawn `cid` value, degrades to L0 rather than failing.
 * `schema=link` alone is permitted by §6.4 but not honoured here.
 */
enum class Rung(val preferenceApplied: String?, val setRendering: SetRendering?) {
    L0(null, null),
    L1("forms=link", SetRendering.EMBEDDED_SCHEMAS),
    L2("forms=link, schema=link", SetRendering.SCHEMA_POINTERS),
    ;

    companion object {
        private const val BY_LINK = "link"

        fun fromPrefer(header: String?): Rung {
            val tokens = parsePreferTokens(header)
            return when {
                tokens["forms"] == BY_LINK && tokens["schema"] == BY_LINK -> L2
                tokens["forms"] == BY_LINK -> L1
                else -> L0
            }
        }

        /** `name=value` pairs from a Prefer header. Names are case-insensitive
         *  (RFC 7240 §2); parameters after `;` and quotes around values are dropped. */
        private fun parsePreferTokens(header: String?): Map<String, String> =
            header.orEmpty().split(',')
                .map { it.substringBefore(';').trim() }
                .filter { it.isNotEmpty() }
                .associate { pref ->
                    pref.substringBefore('=').trim().lowercase() to
                        pref.substringAfter('=', "").trim().removeSurrounding("\"")
                }
    }
}
