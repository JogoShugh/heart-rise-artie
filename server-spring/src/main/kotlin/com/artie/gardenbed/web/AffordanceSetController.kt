package com.artie.gardenbed.web

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
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

    @GetMapping("/affordance-sets/sha256-{hash}")
    suspend fun getAffordanceSet(
        @PathVariable hash: String,
        @RequestHeader("Prefer", required = false) prefer: String?
    ): ResponseEntity<Any> {
        val status = JsonSchemaDialectSupport.statusByHash[hash]
            ?: return ResponseEntity.notFound().build()

        val embed = prefer?.contains("embed=schemas") == true
        val body = JsonSchemaDialectSupport.affordanceSetBody(status, embed)

        val builder = ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/hal+json;profile=\"$JBADEAU_PROFILE\""))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
            .header(HttpHeaders.VARY, "Prefer")

        if (embed) {
            builder.header("Preference-Applied", "embed=schemas")
        }

        return builder.body(body)
    }
}
