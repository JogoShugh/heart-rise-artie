package com.artie.heart.sims.library

import com.artie.heart.Signatures
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.URI
import java.security.PublicKey
import java.time.LocalDate
import java.util.Base64

/**
 * How the two RISE parties find each other's keys, through links, wherever they run: the library reads the keys a
 * request's envelope names; Ana reads the keys her watch links, never a link an answer carries. And what the RISE
 * example starts from: The Hobbit, with a hold waiting, and Ana's three replies.
 */
object RiseParties {
    private val mapper = ObjectMapper()

    /** Ana Torres's loan of The Hobbit, which a hold keeps from being renewed. */
    val HOBBIT = Loan(
        id = 3310,
        title = "The Hobbit",
        author = "J. R. R. Tolkien",
        due = LocalDate.parse("2026-11-12"),
        renewals = 0,
        holdWaiting = true,
    )

    /** Ana's replies: one answered, one failed, one whose request she cancels. */
    val REPLIES = listOf("r-7f3", "r-b04", "r-c21")

    /** The keys at [href], one of Ana's addresses, read from her service ([caller]) as the JWK Set there. */
    fun keysAt(caller: HttpHandler, href: String): Map<String, PublicKey> =
        keySet(caller(Request(Method.GET, URI(href).rawPath)).bodyString())

    /** The keys the watch at [watch] links, read from the library ([library]) as Ana, with her card. */
    fun watchKeys(library: HttpHandler, watch: String): Map<String, PublicKey> {
        val card = Base64.getEncoder().encodeToString("${HeartLibrary.CARD}:$PIN".toByteArray())
        val read = library(
            Request(Method.GET, URI(watch).rawPath).header("Authorization", "Basic $card")
                .header("Accept", "application/hal+json"),
        )
        val keys = mapper.readTree(read.bodyString())["_links"]["hr:keys"]["href"].asText()
        return keySet(library(Request(Method.GET, URI(keys).rawPath)).bodyString())
    }

    private fun keySet(json: String): Map<String, PublicKey> =
        mapper.readTree(json)["keys"].associate { jwk ->
            jwk["kid"].asText() to Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))
        }

    private const val PIN = "1234"
}
