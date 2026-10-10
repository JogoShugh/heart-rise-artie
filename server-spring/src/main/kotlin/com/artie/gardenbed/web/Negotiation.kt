package com.artie.gardenbed.web

import com.artie.heart.Dialect
import org.springframework.http.HttpHeaders
import com.artie.heart.Negotiation as Core

/** The HTTP side of negotiation: reads the request headers and lets heart-core decide (SPEC §6.1, §6.4). */
object Negotiation {
    const val ACCEPT_PROFILE = Core.ACCEPT_PROFILE
    const val JSON_HOME = Core.JSON_HOME

    fun dialect(headers: HttpHeaders): Dialect =
        Core.dialect(headers.getFirst(HttpHeaders.ACCEPT), headers.getFirst(ACCEPT_PROFILE))

    fun wantsJsonHome(headers: HttpHeaders): Boolean = Core.wantsJsonHome(headers.getFirst(HttpHeaders.ACCEPT))

    fun profileLinks(dialect: Dialect): String = Core.profileLinks(dialect)
}
