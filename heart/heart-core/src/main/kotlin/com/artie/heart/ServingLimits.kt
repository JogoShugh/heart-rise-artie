package com.artie.heart

/**
 * How much a server serves, set once at startup and shared by every binding.
 * A test suite may change these per scenario to stand up servers of each kind.
 *
 * - [maxRung] (SPEC §6.11.1): the highest rung served; L2 by default. A
 *   server just starting on the ladder runs at L0: it ignores `Prefer`, sends
 *   no `Preference-Applied`, and its home document declares no `accept-prefer`.
 * - [pageSize] (SPEC §6.11): the most items in one page of a collection.
 *
 * The Spring bindings read them from `heart.max-rung` and `heart.page-size`
 * (environment `HEART_MAX_RUNG`, `HEART_PAGE_SIZE`).
 */
class ServingLimits(maxRung: Rung = Rung.L2, pageSize: Int = DEFAULT_PAGE_SIZE) {
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
