package com.artie.heart.tck

import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** What the last response said in its status line and headers. */
class TckHeaderSteps(private val world: TckWorld) {
    /** The formats the last answer was expected to name, each its type and, where it has one, its profile. */
    private var named: List<Map<String, String?>> = emptyList()

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

    @Then("the response states its profile {string}")
    fun contentProfile(profile: String) =
        assertEquals("<$profile>", world.last.responseHeaders.getFirst("Content-Profile"))

    @Then("the response links these alternates")
    fun alternates(table: DataTable) {
        named = table.asMaps().map { row -> row.filterValues { !it.isNullOrEmpty() } }
        val links = world.last.responseHeaders[HttpHeaders.LINK].orEmpty().joinToString(", ")
        named.forEach { format ->
            val profile = format["profile"]?.let { "; profile=\"$it\"" }.orEmpty()
            assertTrue("rel=\"alternate\"; type=\"${format["type"]}\"$profile" in links, links)
        }
    }

    @Then("the refusal lists the same alternates")
    fun refusalAlternates() {
        val listed = ObjectMapper().readTree(world.last.responseBody)["alternates"]
        assertEquals(named, listed.map { format -> format.properties().associate { (k, v) -> k to v.asText() } })
    }

    @Then("Preference-Applied is {string}")
    fun preferenceApplied(value: String) =
        assertEquals(value, world.last.responseHeaders.getFirst("Preference-Applied"))

}
