package steps

import heart.HeartEmbedded
import heart.HeartLink
import heart.HeartRepresentation
import heart.Manifest
import heart.Related
import heart.mcp.Views
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What heart-mcp shows of a resource that embeds others (SPEC §6.1), checked on its view directly. */
class EmbeddedViewSteps(private val shown: HeldView) {
    private var held: HeartRepresentation
        get() = shown.held
        set(value) { shown.held = value }
    private var view: JsonObject
        get() = shown.view
        set(value) { shown.view = value }

    @Given("heart-mcp holds a resource that embeds these resources")
    fun holds(table: DataTable) {
        val embedded = table.asMaps().groupBy({ it.getValue("relation") }) { row ->
            val login = row.getValue("login")
            HeartEmbedded(ADDRESS + login, row.getValue("title"), buildJsonObject { put("login", login) })
        }
        val state = buildJsonObject { put("number", NUMBER) }
        val related = Related(embedded = embedded)
        held = HeartRepresentation(ADDRESS + "pulls/482", state, Manifest(), "\"seq-1\"", related = related)
    }

    @Given("heart-mcp holds a resource with these links")
    fun holdsLinks(table: DataTable) {
        val links = table.asMaps().groupBy({ it.getValue("relation") }) { row ->
            HeartLink(
                ADDRESS + row.getValue("relation").substringAfter(':'),
                type = row["type"]?.ifEmpty { null },
                templated = row.getValue("templated").toBoolean(),
            )
        }
        held = HeartRepresentation(ADDRESS, buildJsonObject { }, Manifest(), "\"seq-1\"", related = Related(links))
    }

    @Then("the view shows exactly these links")
    fun showsLinks(table: DataTable) {
        val shown = view.getValue("links").jsonArray.map { it.jsonObject.getValue("rel").jsonPrimitive.content }
        assertEquals(table.asMaps().map { it.getValue("relation") }, shown)
    }

    @When("heart-mcp shows what it holds")
    fun shows() {
        view = Views.of(held, read = { shown.readable[it] })
    }

    @Then("the view embeds these resources")
    fun embedsThese(table: DataTable) {
        val shown = view.getValue("embedded").jsonArray.map { it.jsonObject }.map {
            mapOf(
                "relation" to it.getValue("rel").jsonPrimitive.content,
                "title" to it.getValue("title").jsonPrimitive.content,
                "login" to it.getValue("state").jsonObject.getValue("login").jsonPrimitive.content,
            )
        }
        assertEquals(table.asMaps(), shown)
    }

    @Then("a model may follow each of them by relation and title")
    fun followable() {
        val each = held.embeddedByRel.flatMap { (rel, list) -> list.map { rel to it.title } }
        assertTrue(Views.followable(held).containsAll(each), "followable: ${Views.followable(held)}")
    }

    @Then("no address appears in the view")
    fun noAddress() = assertFalse(view.toString().contains(ADDRESS), "an address in $view")

    private companion object {
        const val ADDRESS = "http://service.test/"
        const val NUMBER = 482
    }
}
