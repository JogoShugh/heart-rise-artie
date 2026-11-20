package com.artie.heart.tck

import com.artie.heart.ContentAddress
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** An act's target as a resource of its own (SPEC §6.3): read, revalidated, and explained when not offered. */
class TckActTargetSteps(private val fixture: TckFixture, private val world: TckWorld) {

    @When("^the client gets (the offered act|the absent act)'s target as it got the resource$")
    fun getTarget(act: String) {
        world.get(target(act), world.heldHeaders)
    }

    @When("the client gets the offered act's target again with the ETag it was given")
    fun getTargetAgain() {
        val etag = checkNotNull(world.last.responseHeaders.eTag) { "the act's target carries no ETag" }
        world.get(target(OFFERED), world.heldHeaders + (HttpHeaders.IF_NONE_MATCH to etag))
    }

    @Then("the response's ETag is the resource's")
    fun ownersETag() = assertEquals(world.heldETag, world.last.responseHeaders.eTag)

    @Then("the response links {string} to the resource")
    fun linksOwner(rel: String) =
        assertEquals(world.resource, world.json.path("_links").path(rel).path("href").asText())

    @Then("the response links the offered act's form in the resource's affordance set, with the form's own integrity")
    fun linksForm() {
        val link = world.json.path("_links").path(FORM_REL)
        val (set, fragment) = link.path("href").asText().split('#', limit = 2)
        // The very set the resource links in the same negotiation: the form is shared, never copied.
        val owner = world.get(world.resource, world.heldHeaders).responseBody ?: ByteArray(0)
        val ownersSet = ObjectMapper().readTree(owner).path("_links").path("hr:forms").path("href").asText(null)
        if (ownersSet != null) assertEquals(ownersSet, set)
        val bytes = world.get(set).responseBody ?: ByteArray(0)
        assertEquals(link.path("integrity").asText(), ContentAddress.integrity(String(bytes, Charsets.UTF_8)))
        assertEquals("/_forms/${fixture.offered.key.replace("~", "~0").replace("/", "~1")}", fragment)
        val form = ObjectMapper().readTree(bytes).at(fragment)
        assertTrue(!form.isMissingNode, "no form at #$fragment in $set")
        assertEquals(form.path("hr:integrity").asText(), link.path("hr:integrity").asText())
    }

    @Then("the problem's detail gives the absent act's reason")
    fun detailGivesReason() {
        val detail = world.json.path("detail").asText()
        assertTrue(checkNotNull(fixture.absent.reason) in detail, "detail: $detail")
    }

    private fun target(act: String): String {
        val chosen = if (act == OFFERED) fixture.offered else fixture.absent
        return "${world.resource}/actions/${chosen.path}"
    }

    private companion object {
        const val OFFERED = "the offered act"
        const val FORM_REL = "hr:form"
    }
}
