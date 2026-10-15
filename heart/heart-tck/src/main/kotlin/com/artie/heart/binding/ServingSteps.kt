package com.artie.heart.binding

import com.artie.heart.Rung
import com.artie.heart.ServingLimits
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** How much the server serves (its limits), and what it says it chose. */
class ServingSteps(private val http: HttpWorld, private val limits: ServingLimits) {

    /** The limits are one object for the whole run; every scenario starts at the defaults. */
    @After
    fun restoreLimits() {
        limits.maxRung = Rung.L2
        limits.pageSize = ServingLimits.DEFAULT_PAGE_SIZE
    }

    @Given("the server lists {int} lamps per page")
    fun pageSize(size: Int) {
        limits.pageSize = size
    }

    @Given("the server serves rungs up to {word}")
    fun servesUpTo(rung: String) {
        limits.maxRung = Rung.valueOf(rung)
    }

    @Then("the response carries its forms under {string}")
    fun formsUnder(key: String) = assertTrue(http.json.has(key), "no $key in ${http.json}")

    @Then("the response has no Preference-Applied header")
    fun noPreferenceApplied() = assertNull(http.last.responseHeaders.getFirst(PREFERENCE_APPLIED))

    @Then("Preference-Applied is {string}")
    fun preferenceApplied(value: String) = assertEquals(value, http.last.responseHeaders.getFirst(PREFERENCE_APPLIED))

    @Then("Preference-Applied is absent")
    fun preferenceAppliedAbsent() = noPreferenceApplied()

    @Then("the home document's {string} hint {string} is {string}")
    fun hint(rel: String, hint: String, value: String) =
        assertEquals(value, hints(rel).path(hint).joinToString(", ") { it.asText() })

    @Then("the home document's {string} hint {string} is absent")
    fun hintAbsent(rel: String, hint: String) = assertFalse(hints(rel).has(hint), "${hints(rel)}")

    private fun hints(rel: String): JsonNode =
        http.json.path("resources").fields().asSequence().first { it.key.endsWith(rel) }.value.path("hints")

    private companion object {
        const val PREFERENCE_APPLIED = "Preference-Applied"
    }
}
