package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.*
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaTypeSteps(private val ctx: ClientContext) {

    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val jsonMapper = ObjectMapper()

    @Given("the following cold-start HAL response:")
    fun givenResponse(body: String) {
        ctx.enterBody = jsonMapper.writeValueAsString(yamlMapper.readTree(body))
    }

    @And("the Content-Type declares the H.E.A.R.T. dual profile")
    fun givenDualProfileContentType() {
        ctx.contentType = """application/hal+json; profile="https://github.com/jbadeau/hal-schema-forms https://github.com/jogoshugh/heart-rise""""
    }

    @When("the client parses the response")
    fun whenClientParses() {
        ctx.enqueueEnterResponse()
        ctx.current = ctx.client.enter("https://example.com/")
    }

    @Then("the {string} affordance has:")
    fun thenAffordanceHas(rel: String, fields: List<AffordanceFields>) {
        val form = ctx.current.affordance(rel) ?: error("No affordance with rel '$rel'")
        val expected = fields.single()
        assertEquals(expected.href, form.href)
        assertEquals(expected.hash, form.hash)
    }

    @Then("the {string} field in the {string} schema has the {string} keyword")
    fun thenSchemaFieldHasKeyword(property: String, rel: String, keyword: String) {
        val form = ctx.current.affordance(rel) ?: error("No affordance with rel '$rel'")
        val props = form.schema[PROPERTIES]?.jsonObject ?: error("Schema for '$rel' has no '$PROPERTIES'")
        val prop = props[property]?.jsonObject ?: error("No property '$property' in '$rel' schema")
        assertTrue(prop.containsKey(keyword), "Expected keyword '$keyword' on property '$property'")
    }

    @Then("the {string} schema marks {string} as required")
    fun thenSchemaMarksRequired(rel: String, field: String) {
        val form = ctx.current.affordance(rel) ?: error("No affordance with rel '$rel'")
        val requiredFields = form.schema[REQUIRED]?.jsonArray?.map { it.jsonPrimitive.content }
            ?: error("Schema for '$rel' has no '$REQUIRED' array")
        assertTrue(requiredFields.contains(field), "Expected '$field' in '$REQUIRED' for '$rel'")
    }

    @Then("the resource state contains:")
    fun thenResourceStateContains(entries: List<StateEntry>) {
        entries.forEach { (key, expected) ->
            val actual = ctx.current.state[key]?.jsonPrimitive?.content
                ?: error("Resource state missing key '$key'")
            assertEquals(expected, actual)
        }
    }

    @And("the resource state does not contain the key {string}")
    fun thenResourceStateDoesNotContain(key: String) {
        assertFalse(ctx.current.state.containsKey(key), "Resource state should not contain '$key'")
    }
}
