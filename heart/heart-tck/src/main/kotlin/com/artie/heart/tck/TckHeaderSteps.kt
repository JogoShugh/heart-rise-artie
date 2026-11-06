package com.artie.heart.tck

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** What the last response said in its status line and headers. */
class TckHeaderSteps(private val world: TckWorld) {
    @Then("the response status is {int}")
    fun status(code: Int) =
        assertEquals(code, world.last.status.value(), String(world.last.responseBody ?: ByteArray(0)))

    @Then("the response has Cache-Control {string}")
    fun cacheControl(value: String) = assertEquals(value, world.last.responseHeaders.cacheControl)

    @Then("the response varies on {string}")
    fun varies(value: String) = assertEquals(value, world.last.responseHeaders.getFirst(HttpHeaders.VARY))

    @Then("the response links its profile {string}")
    fun profile(profile: String) {
        val link = world.last.responseHeaders.getFirst(HttpHeaders.LINK).orEmpty()
        assertTrue("<$profile>; rel=\"profile\"" in link, link)
    }

    @Then("Preference-Applied is {string}")
    fun preferenceApplied(value: String) =
        assertEquals(value, world.last.responseHeaders.getFirst("Preference-Applied"))

}
