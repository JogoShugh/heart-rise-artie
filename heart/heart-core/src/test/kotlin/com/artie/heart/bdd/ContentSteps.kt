package com.artie.heart.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.Layers
import com.artie.heart.SharedContent
import com.artie.heart.fixtures.Lamps
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals

/** Naming bodies by their address, and gathering every type's content in one place. */
class ContentSteps {
    private var named: Result<Map<String, String>>? = null
    private var shared: SharedContent? = null
    private var single: Layers<*, *>? = null

    @When("these bodies are named by their addresses")
    fun namedByAddress(table: DataTable) {
        named = runCatching { SharedContent.named(bodies(table)) }
    }

    @When("these bodies are named by a naming that gives every body the name {string}")
    fun namedByOneName(name: String, table: DataTable) {
        named = runCatching { SharedContent.named(bodies(table)) { name } }
    }

    @Then("{int} bodies are named")
    fun bodiesNamed(count: Int) = assertEquals(count, named!!.getOrThrow().size)

    @Then("each named body hashes to its name")
    fun eachHashes() = named!!.getOrThrow().forEach { (name, body) -> assertEquals(name, ContentAddress.address(body)) }

    @Then("naming them is refused, saying two different bodies share the name {string}")
    fun refused(name: String) {
        val failure = named!!.exceptionOrNull()
        assertEquals(IllegalStateException::class, failure?.let { it::class })
        assertEquals("two different bodies share the name $name", failure?.message)
    }

    @When("the content of the lamp type, declared twice, is gathered for the server")
    fun gatheredTwice() {
        val profile = Lamps.profile("home")
        single = Layers(Lamps.type(profile))
        shared = SharedContent(listOf(Layers(Lamps.type(profile)), Layers(Lamps.type(profile))))
    }

    @Then("the server holds each affordance set, schema and explanation of the lamp type once")
    fun heldOnce() {
        val one = single!!
        val all = shared!!
        assertEquals(one.affordanceSets, all.affordanceSets)
        assertEquals(one.schemas, all.schemas)
        assertEquals(one.explanations, all.explanations)
    }

    /** `{"a":1}, {"a":2} and {"a":1}` as the three JSON texts. */
    private fun bodies(table: DataTable): List<String> = table.asMaps().map { it.getValue("body") }
}
