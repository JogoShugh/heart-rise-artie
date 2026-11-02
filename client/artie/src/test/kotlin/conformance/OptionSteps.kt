package conformance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Steps for templates.feature and options.feature: a safe form's address, and options at their edges. */
class OptionSteps(private val world: World) {
    private val actor get() = world.actor

    @Given("the {word} service's job offers {string} as {} to {string}, with pointers {}")
    fun offersGet(service: String, form: String, kind: String, target: String, pointers: String) {
        val templated = kind != "an untemplated GET"
        val shape = """{"_links": {"target": {"href": "$target", "templated": $templated}}, "method": "GET",
            "schema": {"type": "object"}, "hr:templatePointers": $pointers}"""
        val faults = world.sims.faults("$service.test")
        faults.body.reshaped[form] = faults.body.json(shape)
    }

    @Given("the {word} service serves {string} with the validator {string} and the body {}")
    fun servesWithValidator(service: String, path: String, validator: String, body: String) =
        world.sims.faults("$service.test").serve(path, HTTP_OK, body, "\"$validator\"")

    @Then("the values for {string} of {string} are {}")
    fun values(property: String, form: String, values: String) =
        assertEquals(Json.parseToJsonElement(values), JsonArray(choices(form)[property].orEmpty().map { it.value }))

    @Then("the form {string} has choices for exactly {string}")
    fun choicesFor(form: String, property: String) = assertEquals(setOf(property), choices(form).keys)

    @Then("the last read accepted {string}, with no cache")
    fun acceptedPlain(accept: String) {
        val read = lastRead()
        assertEquals(accept to "no-cache", read.header("Accept") to read.header("Cache-Control"))
    }

    @Then("the last read carried no validator")
    fun noValidatorSent() = assertNull(lastRead().header("If-None-Match"))

    private fun choices(form: String) =
        checkNotNull(actor.client.choices(form)) { "$form is not offered" }.of(JsonObject(emptyMap()))

    private fun lastRead() = actor.log.last { it.method == "GET" && "/affordance-sets/" !in it.url }

    private companion object {
        const val HTTP_OK = 200
    }
}
