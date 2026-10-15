package com.artie.heart

/**
 * How much a server serves, set once at startup and shared by every binding.
 * A test suite may change these per scenario to stand up servers of each kind.
 *
 * - [maxRung] (SPEC §6.11.1): the highest rung served; L2 by default. A
 *   server just starting on the ladder runs at L0: it ignores `Prefer`, sends
 *   no `Preference-Applied`, and its home document declares no `accept-prefer`.
 * - [pageSize] (SPEC §6.11): the most items in one page of a collection.
 * - [affordanceLinks]: whether each plain JSON answer about a resource links
 *   its state's affordance set, and the OpenAPI description says so. Off by
 *   default, since it changes a published description's bytes.
 *
 * The Spring bindings read them from `heart.max-rung`, `heart.page-size` and
 * `heart.affordance-links` (environment `HEART_MAX_RUNG`, `HEART_PAGE_SIZE`,
 * `HEART_AFFORDANCE_LINKS`).
 */
class ServingLimits(
    maxRung: Rung = Rung.L2,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    val affordanceLinks: Boolean = false,
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
