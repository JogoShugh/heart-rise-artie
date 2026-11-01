package conformance

import com.artie.heart.sims.Simulator
import com.artie.heart.sims.deploy.Region
import heart.Choice
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Steps for the simulated deploy service, and for a form's choices, as choices.feature names them. */
class DeployServiceSteps(private val world: World) {
    private val actor get() = world.actor
    private var resolved: List<Map<String, List<Choice>>> = emptyList()

    @Given("the deploy service's app {string} has the regions")
    fun regions(app: String, table: DataTable) {
        val regions = table.asMaps().groupBy({ it.getValue("environment") }) { row ->
            Region(row.getValue("name"), row.getValue("label"), row.getValue("available").toBoolean())
        }
        world.sims.setRegions(app, regions)
    }

    @Given("the deploy service has the releases")
    fun releases(table: DataTable) = table.asMaps().forEach { row ->
        world.sims.publishRelease(row.getValue("app"), row.getValue("version"), row.getValue("status"))
    }

    @When("it enters the app {string}")
    fun enterApp(name: String) {
        world.rep = actor.client.enter("http://${Simulator.DEPLOY}/apps/$name")
    }

    @Then("the choices for {string} of {string}, by {string}, are")
    fun choicesBy(property: String, form: String, field: String, table: DataTable) {
        table.asMaps().groupBy { it.getValue(field) }.forEach { (value, rows) ->
            val fields = JsonObject(mapOf(field to JsonPrimitive(value)))
            assertEquals(rows.map { it - field }, rowsOf(choices(form, fields)[property].orEmpty()), "$field $value")
        }
    }

    @Then("the choices for {string} of {string} are")
    fun choicesOf(property: String, form: String, table: DataTable) =
        assertEquals(table.asMaps(), rowsOf(choices(form, JsonObject(emptyMap()))[property].orEmpty()))

    @When("it resolves the choices for {string} of {string} twice")
    fun resolveTwice(@Suppress("UNUSED_PARAMETER") property: String, form: String) {
        resolved = List(2) { choices(form, JsonObject(emptyMap())) }
    }

    @Then("the second answer held the same choices as the first")
    fun sameChoices() {
        assertTrue(resolved.first().values.flatten().isNotEmpty(), "no choices")
        assertEquals(resolved.first(), resolved.last())
    }

    @Then("the releases were read at {string}")
    fun releasesReadAt(path: String) = assertEquals(listOf(path), releaseReads().map { it.url })

    @Then("the second read of the releases carried the ETag of the first, and was answered 304")
    fun revalidated() {
        val (first, second) = releaseReads().also { assertEquals(2, it.size, "$it") }
        assertEquals(HTTP_OK, first.status)
        assertEquals(first.etag, second.headers["If-None-Match"])
        assertEquals(HTTP_NOT_MODIFIED, second.status)
    }

    private fun choices(form: String, fields: JsonObject) =
        checkNotNull(actor.client.choices(form)) { "$form is not offered" }.of(fields)

    private fun rowsOf(choices: List<Choice>) =
        choices.map { mapOf("value" to it.value.jsonPrimitive.content, "prompt" to it.prompt) }

    private fun releaseReads() =
        world.sims.log.filter { it.service == Simulator.DEPLOY && it.url.startsWith("/releases") }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_NOT_MODIFIED = 304
    }
}
