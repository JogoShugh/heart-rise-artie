package com.artie.heart.sims.rise

import com.artie.heart.Signatures
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.URI
import java.security.PublicKey

/**
 * How a RISE party finds the other's keys over HTTP, by links alone: the JWK Set at an address, and the set a
 * resource links as `hr:keys` (a request a caller made, which its answers are checked by).
 */
class Keys(private val client: HttpHandler, private val authorization: String? = null) {
    private val mapper = ObjectMapper()

    /** The keys in the JWK Set at [href], by key id. */
    fun at(href: String): Map<String, PublicKey> =
        mapper.readTree(client(authorized(Request(Method.GET, href))).bodyString())["keys"].associate { jwk ->
            jwk["kid"].asText() to Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))
        }

    /** The keys the resource at [href] links as `hr:keys`. */
    fun linkedFrom(href: String): Map<String, PublicKey> {
        val read = client(authorized(Request(Method.GET, href)).header("Accept", "application/hal+json"))
        val keys = mapper.readTree(read.bodyString())["_links"]["hr:keys"]["href"].asText()
        return at(URI(href).resolve(keys).toString())
    }

    private fun authorized(request: Request) = authorization?.let { request.header("Authorization", it) } ?: request
}
