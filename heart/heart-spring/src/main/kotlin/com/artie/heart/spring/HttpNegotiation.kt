package com.artie.heart.spring

import com.artie.heart.Dialect
import com.artie.heart.Negotiation
import org.springframework.http.HttpHeaders

/** The HTTP side of negotiation: reads the request headers and lets heart-core decide (SPEC §6.1, §6.4). */
object HttpNegotiation {
    const val ACCEPT_PROFILE = Negotiation.ACCEPT_PROFILE
    const val PREFER = "Prefer"

    fun dialect(headers: HttpHeaders): Dialect =
        Negotiation.dialect(headers.getFirst(HttpHeaders.ACCEPT), headers.getFirst(ACCEPT_PROFILE))

    fun wantsJsonHome(headers: HttpHeaders): Boolean = Negotiation.wantsJsonHome(headers.getFirst(HttpHeaders.ACCEPT))

    fun profileLinks(dialect: Dialect): String = Negotiation.profileLinks(dialect)
}
