package com.artie.gardenbed.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

/**
 * Serves the garden ALPS profile (SPEC §8.5). Each form relation,
 * `garden:<id>`, expands to `/profiles/garden#<id>`, a descriptor here. A
 * client fetches it only when it wants more than a form's inline text, and
 * may cache it for a day; the ETag lets it revalidate cheaply.
 */
@RestController
class ProfileController {

    @GetMapping(GardenProfile.PATH)
    fun garden(): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(GardenProfile.ALPS_MEDIA_TYPE))
            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
            .eTag("\"alps-$etag\"")
            .body(body)

    private companion object {
        const val ETAG_HEX = 16
        val body: String = ObjectMapper().writeValueAsString(GardenProfile.alps)
        val etag: String = MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(ETAG_HEX)
    }
}
