package com.artie.heart.tck

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * A refusal is a state too: its RFC 9457 problem document carries `_links`, an extension member, so a client goes on
 * by links rather than by addresses it knows.
 */
class TckRefusalSteps(private val world: TckWorld) {

    /** [target] is `the resource` (the fixture's) or a quoted path, such as `"/"`. */
    @Then("the problem links {string} to {}")
    fun links(rel: String, target: String) {
        val href = if (target == "the resource") world.resource else target.trim('"')
        val link = world.json.path("_links").path(rel)
        assertEquals(href, link.path("href").asText(null), "the problem's links: ${world.json.path("_links")}")
    }
}
