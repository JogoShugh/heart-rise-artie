package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_FORMS_ACCEPT
import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import com.artie.gardenbed.domain.GardenBedStatus
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import java.io.File

/**
 * Gathers every kind of response the garden gives, and checks that each HEART
 * identifier in them has a page in the identifier site (docs/, built from
 * standards/identifiers.json by scripts/build-identifier-pages.sh).
 */
class IdentifierSteps(
    private val http: HttpWorld,
    private val beds: InMemoryBeds,
    private val store: BedEventLog,
    private val client: WebTestClient,
) {
    private val found = sortedSetOf<String>()

    @When("a client gathers every kind of response the garden gives")
    fun gather() {
        // The bookmark, in each format it is served in.
        listOf(HAL_SCHEMA_FORMS_ACCEPT, HAL_FORMS_ACCEPT, "application/json-home").forEach { accept ->
            collect(http.get("/", mapOf("Accept" to accept)))
        }
        // Each bed state at each rung, with the shared layers it links to.
        for (status in GardenBedStatus.entries) {
            reset(status)
            for (prefer in listOf(null, "forms=link", "forms=link, schema=link")) {
                val headers = mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT) + listOfNotNull(prefer?.let { "Prefer" to it })
                val bed = http.get("/beds/2", headers).also(::collect)
                shared(bed.json).forEach { collect(http.get(it)) }
                // Each act's target is a resource too (SPEC §6.3), read in the same negotiation.
                bed.json.path("_forms").forEach { form ->
                    collect(http.get(form.path("_links").path("target").path("href").asText(), headers))
                }
            }
        }
        refusals()
    }

    /** Each refusal the garden can give, provoked once. */
    private fun refusals() {
        reset(GardenBedStatus.PLANTED)
        val read = http.get("/beds/2", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
        val etag = checkNotNull(read.headers.eTag)
        val water = "/beds/2/actions/water-cell"
        val accept = mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT)
        collect(http.post(water, """{"milliliters": 1}""", accept + ("If-Match" to "\"stale\"")))
        collect(http.post(water, """{"milliliters": 1}""", accept))
        collect(http.post("/beds/2/actions/harvest", "{}", accept + ("If-Match" to etag)))
        collect(http.post(water, """{"milliliters": 0}""", accept + ("If-Match" to etag)))
        // 400 ml leaves room for 600 more; 700 would waterlog the bed, which a rule refuses.
        http.post(water, """{"milliliters": 400}""", accept + ("If-Match" to etag))
        val wet = checkNotNull(http.get("/beds/2", accept).headers.eTag)
        collect(http.post(water, """{"milliliters": 700}""", accept + ("If-Match" to wet)))
        // And a caller with no credential.
        val bare = client.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
        val refused = bare.get().uri("/beds/2").exchange().expectBody().returnResult()
        found += IDENTIFIER.findAll(String(refused.responseBodyContent ?: ByteArray(0))).map { it.value }
    }

    @Then("every HEART identifier in them has a page in the identifier site")
    fun everyHasAPage() {
        val missing = found.filterNot { page(it).isFile }
        assertTrue(found.isNotEmpty(), "no identifiers were found")
        assertTrue(missing.isEmpty(), "no page for: ${missing.joinToString()}; run scripts/build-identifier-pages.sh")
    }

    @Then("the identifiers it found include:")
    fun include(expected: List<String>) {
        val missing = expected.drop(1).filterNot { it in found }
        assertTrue(missing.isEmpty(), "not found: ${missing.joinToString()}; found ${found.joinToString()}")
    }

    private fun reset(status: GardenBedStatus) {
        beds.reset()
        store.reset()
        putBed(beds, store, 2, status)
    }

    private fun collect(exchange: Exchange) {
        val text = String(exchange.bytes) + exchange.headers.toString()
        found += IDENTIFIER.findAll(text).map { it.value }.filterNot { '{' in it }
        val isJson = exchange.bytes.isNotEmpty() && exchange.bytes[0] == '{'.code.toByte()
        if (isJson) found += expandedRelations(exchange.json)
    }

    /** `hr:` relations are written as CURIEs; each expands to a URI with the `hr` template. */
    private fun expandedRelations(json: JsonNode): List<String> {
        val links = json.path("_links")
        val template = links.path("curies").firstOrNull { it.path("name").asText() == "hr" }?.path("href")?.asText()
            ?: return emptyList()
        return links.fieldNames().asSequence().filter { it.startsWith("hr:") }
            .map { template.replace("{rel}", it.removePrefix("hr:")) }.toList()
    }

    /** The forms link, the unavailable link and each schema a set links to. */
    private fun shared(bed: JsonNode): List<String> {
        val links = bed.path("_links")
        val set = links.path("hr:forms").path("href").asText().takeIf { it.isNotEmpty() }
        val unavailable = links.path("hr:unavailable").path("href").asText().takeIf { it.isNotEmpty() }
        val schemas = set?.let { http.get(it).json.findValues("\$ref").map { ref -> ref.asText() } }.orEmpty()
        return listOfNotNull(set, unavailable) + schemas
    }

    private companion object {
        val IDENTIFIER = Regex("""https://w3id\.org/heart-rise(/[A-Za-z0-9._~/{}-]*)?""")
        val SITE = File("../docs")

        /** The page for an identifier: the base is the site's index, any variable is the vars page. */
        fun page(identifier: String): File {
            val path = identifier.removePrefix("https://w3id.org/heart-rise").trim('/')
            return when {
                path.isEmpty() -> File(SITE, "index.html")
                path.startsWith("vars/") -> File(SITE, "vars/index.html")
                else -> File(SITE, "$path/index.html")
            }
        }
    }
}
