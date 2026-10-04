package com.artie.gardenbed.web

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Layer 2 of the three-layer scheme: the SET of currently-allowed
 * affordances, content-addressed and immutable. Every bed currently in
 * the same GardenBedStatus points at the exact same URL here -- fetch
 * it once for any one bed in that status, and every other bed in that
 * status is a cache hit, never touching the origin again.
 *
 * NO bedId parameter, deliberately -- an earlier version of this
 * controller required one as a query param, which silently defeated
 * the whole point: `?bedId=2` and `?bedId=5` are different URLs to any
 * real HTTP cache, so nothing would have actually been shared across
 * beds despite the hash-named path. Fixed by leaving `target.href` as
 * a real URI Template (`/beds/{bedId}/actions/...`, `templated: true`)
 * in the response body instead -- the client expands `{bedId}` itself
 * before POSTing, same as any other RFC 6570 URI Template in a HAL
 * link. That's what makes this resource genuinely bed-agnostic: the
 * bytes are identical no matter which bed asked, so the URL alone is
 * a valid, shareable cache key with no query string needed at all.
 */
@RestController
class AffordanceSetController {

    /** One address, one representation (SPEC 0.4 §6.1, §6.5): the L1 and
     *  L2 renderings live at different hashes, so this never reads
     *  Prefer and never sends Vary. */
    @GetMapping("/affordance-sets/sha256-{hash}")
    suspend fun getAffordanceSet(@PathVariable hash: String): ResponseEntity<String> {
        // Serve the canonical string, never a Map for Jackson to write:
        // the hash in the URL covers these exact bytes (SPEC.md §6.5).
        val body = JsonSchemaDialectSupport.affordanceSetsByHash[hash]
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/hal+json;profile=\"$JBADEAU_PROFILE\""))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .body(body)
    }
}
