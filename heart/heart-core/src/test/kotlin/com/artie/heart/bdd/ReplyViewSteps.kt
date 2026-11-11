package com.artie.heart.bdd

import com.artie.heart.Replies
import com.artie.heart.Reply
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals

/** What Ana's reply shows: its state, its progress, its problem, its forms and its CURIEs. */
class ReplyViewSteps(private val world: ReplyWorld) {
    private val mapper = ObjectMapper()

    @Then("""^reply (r-\w+) is now (\w+)$""")
    fun now(reference: String, standing: String) = assertEquals(standing, reply(reference).standing)

    @Then("""^reply (r-\w+) shows the percent (\d+) and the note (.*)$""")
    fun shows(reference: String, percent: Int, note: String) {
        val reply = reply(reference)
        assertEquals(percent to mapper.readTree(note).takeUnless { it.isNull }?.asText(), reply.percent to reply.note)
    }

    @Then("""^reply (r-\w+) embeds the problem (.*)$""")
    fun embeds(reference: String, problem: String) {
        val embedded = (layer1(reference)["_embedded"] as? Map<*, *>)?.get("problem")
        assertEquals(mapper.readValue(problem, Map::class.java), embedded)
    }

    @Then("""^reply (r-\w+) links "([^"]+)" to "([^"]+)"$""")
    fun links(reference: String, rel: String, href: String) {
        val link = (layer1(reference)["_links"] as Map<*, *>)[rel] as? Map<*, *>
        assertEquals(href, link?.get("href"), layer1(reference).toString())
    }

    @Then("^the hr profile describes these$")
    fun describes(table: DataTable) {
        val descriptors = ((Replies.PROFILE.alps["alps"] as Map<*, *>)["descriptor"] as List<*>).map { it as Map<*, *> }
        assertEquals(table.asLists().drop(1), descriptors.map { listOf(it["id"], it["type"]) })
    }

    @Then("""^reply (r-\w+) offers these forms$""")
    fun offersForms(reference: String, table: DataTable) {
        val forms = layer1(reference)["_forms"] as Map<*, *>
        val seen = forms.map { (key, form) ->
            form as Map<*, *>
            listOf(
                key.toString(),
                ((form["_links"] as Map<*, *>)["target"] as Map<*, *>)["href"].toString(),
                form["method"].toString(),
                form["contentType"].toString(),
            )
        }
        assertEquals(table.asLists().drop(1), seen)
    }

    @Then("""^reply (r-\w+) declares exactly one CURIE "(\w+)", at "([^"]+)"$""")
    fun curie(reference: String, name: String, href: String) {
        val curies = (layer1(reference)["_links"] as Map<*, *>)["curies"] as List<*>
        assertEquals(listOf(href), curies.map { it as Map<*, *> }.filter { it["name"] == name }.map { it["href"] })
    }

    private fun reply(reference: String): Reply = checkNotNull(world.store.get(reference)) { "no reply $reference" }

    private fun layer1(reference: String): Map<String, Any?> = world.resource.layers.layer1(reply(reference))
}
