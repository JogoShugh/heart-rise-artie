package com.artie.gardenbed.web

import com.artie.heart.Rung
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * How much this server serves, set once at startup. The BDD suite changes
 * these per scenario to stand up servers of each kind.
 *
 * - [maxRung] (SPEC §6.11.1): the highest rung served, from `heart.max-rung`
 *   (environment `HEART_MAX_RUNG`), L2 by default. A server just starting on
 *   the ladder runs at L0: it ignores `Prefer`, sends no `Preference-Applied`,
 *   and its home document declares no `accept-prefer`.
 * - [pageSize] (SPEC §6.11): the most items in one page of a collection,
 *   from `heart.page-size` (environment `HEART_PAGE_SIZE`).
 */
@Component
class ServingLimits(
    @Value("\${heart.max-rung:L2}") maxRung: Rung,
    @Value("\${heart.page-size:$DEFAULT_PAGE_SIZE}") pageSize: Int,
) {
    @Volatile
    var maxRung: Rung = maxRung

    @Volatile
    var pageSize: Int = pageSize

    /** The rung asked for, capped at [maxRung]. */
    fun cap(asked: Rung): Rung = minOf(asked, maxRung)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
