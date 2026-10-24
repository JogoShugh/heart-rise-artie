package steps

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/** SPEC §6.1: the resources a representation embeds, read in place. */
class EmbeddedSteps(private val ctx: ClientContext) {

    @Then("the resource embeds these resources")
    fun embedsThese(table: DataTable) {
        val rows = table.asMaps()
        val read = rows.map { it.getValue("relation") }.distinct().flatMap { rel ->
            ctx.current.embedded(rel).map { resource ->
                mapOf("relation" to rel, "href" to resource.href, "title" to resource.title.orEmpty()) +
                    rows.first().keys.filter { it !in COLUMNS }.associateWith {
                        resource.state[it]?.jsonPrimitive?.content.orEmpty()
                    }
            }
        }
        assertEquals(rows, read)
    }

    @Then("the resource links these templates")
    fun linksTemplates(table: DataTable) = table.asMaps().forEach { row ->
        val link = ctx.current.links(row.getValue("relation")).single()
        assertEquals(row.getValue("href"), link.href)
        assertEquals(true, link.templated, "${row.getValue("relation")} is not templated")
    }

    @Then("the embedded {string} links {string} to the template {string}")
    fun embeddedTemplate(relation: String, rel: String, href: String) {
        val link = ctx.current.embedded(relation).single().related.links.getValue(rel).single()
        assertEquals(href, link.href)
        assertEquals(true, link.templated)
    }

    private companion object {
        val COLUMNS = setOf("relation", "href", "title")
    }
}
