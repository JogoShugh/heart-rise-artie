package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.FORMS_REL
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for SPEC §6.1: dialect by Accept, profile by Accept-Profile and Link rel="profile". */
class ProfileSteps(private val http: HttpWorld) {

    /** A blank value leaves that header off the request. */
    @When("a client gets bed {long} with Accept {string} and Accept-Profile {string}")
    fun getBedNegotiated(id: Long, accept: String, acceptProfile: String) {
        val headers = buildMap {
            if (accept.isNotBlank()) put("Accept", accept)
            if (acceptProfile.isNotBlank()) put("Accept-Profile", acceptProfile)
        }
        http.get("/beds/$id", headers)
    }

    @Then("the response Content-Type is {string}")
    fun contentType(expected: String) = assertEquals(expected, http.last.headers.getFirst("Content-Type"))

    @Then("the response links to profile {string}")
    fun linksToProfile(expected: String) {
        val profiles = http.last.headers["Link"].orEmpty()
            .flatMap { it.split(',') }
            .mapNotNull { PROFILE_LINK.find(it.trim())?.groupValues?.get(1) }
        assertTrue(expected in profiles, "profile links $profiles, Link: ${http.last.headers["Link"]}")
    }

    @Then("the response carries its forms under {string}")
    fun formsKey(key: String) = assertTrue(http.last.json.has(key), "no $key in ${http.last.json}")

    @Then("the response Vary lists {string}")
    fun varyLists(expected: String) = assertEquals(expected.split(", "), http.last.headers.vary)

    @Then("the affordance-set link has type {string} and profile {string}")
    fun affordanceSetLinkHints(type: String, profile: String) {
        val link = http.last.json.path("_links").path(FORMS_REL)
        assertEquals(type, link.path("type").asText(), "link: $link")
        assertEquals(profile, link.path("profile").asText(), "link: $link")
    }

    private companion object {
        val PROFILE_LINK = Regex("""^<([^>]*)>\s*;\s*rel="?profile"?$""")
    }
}
