package com.artie.gardenbed.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * SPEC §6.11.1: the highest rung this server serves, from `heart.max-rung`
 * (environment `HEART_MAX_RUNG`), L2 by default. A server just starting on
 * the ladder runs at L0: it ignores `Prefer`, sends no `Preference-Applied`,
 * and its home document declares no `accept-prefer`. Set once at startup;
 * the BDD suite changes it per scenario to stand up servers at each level.
 */
@Component
class ServingLimits(@Value("\${heart.max-rung:L2}") maxRung: Rung) {
    @Volatile
    var maxRung: Rung = maxRung

    /** The rung asked for, capped at [maxRung]. */
    fun cap(asked: Rung): Rung = minOf(asked, maxRung)
}
