package steps

import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Steps for forms named by link relations and their ALPS descriptors (SPEC §6.1, §8.5). */
class FormRelationSteps(private val ctx: ClientContext) {

    @Then("the {string} form's relation is {string}")
    fun relation(name: String, relation: String) =
        assertEquals(relation, assertNotNull(ctx.current.affordance(name), "no $name").relation)

    @Then("the client describes {string} as {string}")
    fun describes(name: String, doc: String) = assertEquals(doc, ctx.client.describe(name))

    @Then("the request for {string} had header {string} set to {string}")
    fun requestHeader(url: String, name: String, value: String) {
        val request = ctx.transport.requests.last { it.method == "GET" && it.url == url }
        assertEquals(value, request.header(name))
    }
}
