package com.artie.heart.tck

/**
 * What a domain server tells the HEART conformance features about itself: one
 * resource of a declared type, in a state where [offered] is offered and
 * [absent] is not. A domain registers one as a bean in the test context that
 * runs the features.
 */
interface TckFixture {
    /** The declared type's collection, as in `/{collection}/{id}`. */
    val collection: String

    /** An act the resource offers, with a valid body and a body its schema refuses. */
    val offered: TckAct

    /** An act the resource does not offer, with a body for it and the reason the server gives. */
    val absent: TckAct

    /**
     * An Authorization header value the server admits (`Basic ...` or
     * `Bearer ...`). Every request carries it, except in the scenarios that
     * check what a caller with no credential is told.
     */
    val credential: String

    /** Text that finds the resource with the type's search form. */
    val searchTerm: String

    /**
     * An act the bookmark offers for the type, if it offers one: its form key,
     * its path under `/actions/`, a valid body, and a body its schema refuses.
     * The `@bookmark-act` scenarios use it.
     */
    val bookmarkAct: TckAct? get() = null

    /** Puts the server's data back to a known state, with the resource ready; returns its id. */
    suspend fun ready(): Long
}

/**
 * An act as the features use it: its relation as a form key (`garden:water-cell`),
 * its action path, a body, and for the offered act a body its schema refuses
 * and the JSON Pointer that refusal names; for the absent act, its reason.
 */
data class TckAct(
    val key: String,
    val path: String,
    val body: String,
    val invalidBody: String? = null,
    val invalidPointer: String? = null,
    val reason: String? = null,
)
