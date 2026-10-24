package com.artie.heart.http4k

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.Layers
import com.artie.heart.Listing
import com.artie.heart.OpenApiDocument
import com.artie.heart.Profile
import com.artie.heart.SharedContent
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.etag.ETagValidationRequestParser
import org.http4k.core.etag.FieldValue
import org.http4k.core.queries

/**
 * What every caller is served alike: Layers 2 and 3 and the absence
 * explanations (SPEC §6.5, §6.12), each as the exact canonical string its
 * address names, immutable; each ALPS profile (SPEC §8.5); the OpenAPI
 * description; and the lists a domain declares for its forms' choices
 * (HAL-SCHEMA-FORMS-HR Rule 8).
 */
class Published(layers: List<Layers<*, *>>, private val document: OpenApiDocument, private val mapper: ObjectMapper) {
    private val content = SharedContent(layers)
    private val profiles: Map<String, String> = layers.map { it.type.profile }.associateBy { it.name }
        .mapValues { (_, profile) -> mapper.writeValueAsString(profile.alps) }

    fun affordanceSet(address: String): Response =
        immutable(content.affordanceSets[address], Dialect.JSONSCHEMA.mediaType)

    fun schema(address: String): Response = immutable(content.schemas[address], SCHEMA_JSON)

    fun explanation(address: String): Response = immutable(content.explanations[address], JSON)

    /** A client fetches a profile only when it wants more than a form's inline text, and may cache it a day. */
    fun profile(name: String): Response {
        val body = profiles[name] ?: return Response(Status.NOT_FOUND)
        return Response(Status.OK).header("Content-Type", Profile.ALPS_MEDIA_TYPE)
            .header("Cache-Control", "public, max-age=86400")
            .header("ETag", "\"alps-${ContentAddress.address(body)}\"")
            .body(body)
    }

    /** It changes only when the declarations or the version do, so it is revalidated, not cached long. */
    fun openApi(request: Request): Response =
        revalidated(request, document.validator, OpenApiDocument.MEDIA_TYPE) { document.body }

    /** A list for a form's choices, as plain JSON, revalidated by its ETag. */
    fun listing(listing: Listing, request: Request): Response {
        val query = request.uri.queries().distinctBy { it.first }.associate { (name, value) -> name to value.orEmpty() }
        val body = listing.body(query)
        return revalidated(request, listing.validator(body), JSON) { mapper.writeValueAsString(body) }
    }

    private fun immutable(body: String?, type: String): Response =
        if (body == null) {
            Response(Status.NOT_FOUND)
        } else {
            Response(Status.OK).header("Content-Type", type)
                .header("Cache-Control", IMMUTABLE)
                .header("Repr-Digest", ContentAddress.reprDigest(body))
                .body(body)
        }

    companion object {
        const val SCHEMA_JSON = "application/schema+json"
        const val JSON = "application/json"
        const val IMMUTABLE = "public, max-age=31536000, immutable"

        /** A 304 when If-None-Match already names [etag]; otherwise [body], always no-cache. */
        fun revalidated(request: Request, etag: String, type: String, body: () -> String): Response {
            val unchanged = request.header("If-None-Match")?.let { Validators.notModified(it, etag) } == true
            val headers = Response(if (unchanged) Status.NOT_MODIFIED else Status.OK)
                .header("ETag", etag).header("Cache-Control", "no-cache")
            return if (unchanged) headers else headers.header("Content-Type", type).body(body())
        }
    }
}

/** HTTP validators (RFC 9110 §13.1.2), parsed by http4k. */
object Validators {
    /** If-None-Match uses weak comparison; `*` matches any current representation. */
    fun notModified(ifNoneMatch: String, etag: String): Boolean =
        when (val held = ETagValidationRequestParser.parse(ifNoneMatch)) {
            is FieldValue.Wildcard -> true
            is FieldValue.ETags -> held.value.any { "\"${it.value}\"" == etag }
        }
}
