package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import com.artie.gardenbed.domain.GardenBedStatus
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.security.MessageDigest

/** Walks every content-addressed resource a bed links to, to pin their addresses. */
class ContentAddressSteps(
    private val http: HttpWorld,
    private val beds: InMemoryBeds,
    private val store: InMemoryEventStore,
) {
    private val reached = sortedSetOf<String>()
    private val fetched = mutableMapOf<String, Exchange>()

    @When("a client walks bed {long} through every state at L0, L1 and L2, following every content-addressed link")
    fun walk(id: Long) {
        for (status in GardenBedStatus.entries) {
            beds.reset()
            store.reset()
            putBed(beds, store, id, status)
            for (prefer in listOf(null, "forms=link", "forms=link, schema=link")) {
                val headers = mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT) + listOfNotNull(prefer?.let { "Prefer" to it })
                follow(http.get("/beds/$id", headers).json)
            }
        }
    }

    @Then("the addresses it reached are exactly:")
    fun theAddressesAre(table: DataTable) {
        val pinned = table.asMaps().map { it.getValue("address") }.toSortedSet()
        assertEquals(pinned.joinToString("\n"), reached.joinToString("\n"))
    }

    @Then("each one answered 200 with bytes that hash to its address")
    fun eachAnswered() {
        reached.forEach { path ->
            val exchange = fetched.getValue(path)
            assertEquals(200, exchange.status, path)
            val hex = MessageDigest.getInstance("SHA-256").digest(exchange.bytes).joinToString("") { "%02x".format(it) }
            assertEquals(path.substringAfterLast("sha256-"), hex.take(path.substringAfterLast("sha256-").length), path)
        }
    }

    /** Fetches each new content-addressed link found anywhere in [node], and walks what came back. */
    private fun follow(node: JsonNode) {
        addresses(node).filter { reached.add(it) }.forEach { path ->
            val exchange = http.get(path)
            fetched[path] = exchange
            follow(exchange.json)
        }
    }

    private fun addresses(node: JsonNode): List<String> = when {
        node.isObject -> node.properties().flatMap { (key, value) ->
            if (key in LINK_KEYS && value.isTextual && ADDRESS.matches(value.asText())) {
                listOf(value.asText())
            } else {
                addresses(value)
            }
        }
        node.isArray -> node.flatMap { addresses(it) }
        else -> emptyList()
    }

    private companion object {
        val LINK_KEYS = setOf("href", "\$ref", "\$id")
        val ADDRESS = Regex("^/(affordance-sets|schemas|unavailable)/sha256-[0-9a-f]+$")
    }
}
