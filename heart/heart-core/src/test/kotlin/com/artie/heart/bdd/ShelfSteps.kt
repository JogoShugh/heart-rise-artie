package com.artie.heart.bdd

import com.artie.heart.Act
import com.artie.heart.ActRequests
import com.artie.heart.Acted
import com.artie.heart.AlpsType
import com.artie.heart.ContentAddress
import com.artie.heart.HeartResource
import com.artie.heart.JsonTrees
import com.artie.heart.Layers
import com.artie.heart.OpenApiDocument
import com.artie.heart.ResourceType
import com.artie.heart.Rung
import com.artie.heart.fixtures.Shelf
import com.artie.heart.fixtures.Shelves
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** act-methods.feature: the shelf, whose acts are served by every method an act may have. */
@Suppress("UNCHECKED_CAST")
class ShelfSteps {
    private lateinit var type: ResourceType<Shelf, String>
    private lateinit var layers: Layers<Shelf, String>
    private var forms: Map<String, Map<String, Any?>> = emptyMap()
    private var openApi: JsonNode = ObjectMapper().createObjectNode()
    private var refused: String? = null
    private var input: String? = null
    private var act: Act? = null
    private var asked: String = ""

    @Given("a shelf resource type declared with the profile {string}")
    fun aShelfType(name: String) {
        type = Shelves.type(Shelves.profile(name))
        layers = Layers(type)
    }

    @When("a stocked shelf's affordance set is rendered")
    fun setRendered() {
        val body = layers.layer1(Shelves.stocked(SHELF), Rung.L1)
        val link = (body["_links"] as Map<String, Map<String, Any?>>).getValue(Layers.FORMS_REL)
        val set = layers.affordanceSets.getValue((link["href"] as String).removePrefix(Layers.AFFORDANCE_SETS))
        forms = (JsonTrees.plain(set) as Map<String, Any?>)["_forms"] as Map<String, Map<String, Any?>>
    }

    @When("shelf {long} is rendered at L0")
    fun renderedL0(id: Long) {
        forms = layers.layer1(Shelves.stocked(id), Rung.L0)["_forms"] as Map<String, Map<String, Any?>>
    }

    @When("shelf {long} is rendered in HAL-FORMS")
    fun renderedHalForms(id: Long) {
        forms = layers.halForms(Shelves.stocked(id))["_templates"] as Map<String, Map<String, Any?>>
    }

    @Then("its forms say")
    fun formsSay(table: DataTable) = table.asMaps().forEach { row ->
        val form = forms.getValue(row.getValue("form"))
        val target = (form["_links"] as Map<String, Map<String, Any?>>).getValue("target")["href"]
        assertEquals(row.getValue("method"), form["method"], row.toString())
        assertEquals(row["contentType"].orEmpty(), form["contentType"]?.toString().orEmpty(), row.toString())
        assertEquals(row.getValue("target"), target, row.toString())
    }

    @Then("its templates say")
    fun templatesSay(table: DataTable) {
        assertEquals(table.asMaps().map { it.getValue("template") }.toSet(), forms.keys)
        table.asMaps().forEach { row ->
            val template = forms.getValue(row.getValue("template"))
            assertEquals(row.getValue("method"), template["method"], row.toString())
            assertEquals(row["contentType"].orEmpty(), template["contentType"]?.toString().orEmpty(), row.toString())
            assertEquals(row.getValue("target"), template["target"], row.toString())
        }
    }

    @Then("the {string} form's target is templated")
    fun templated(key: String) = assertEquals(true, target(key)["templated"])

    @Then("the {string} form's target is not templated")
    fun notTemplated(key: String) = assertFalse("templated" in target(key))

    @When("the OpenAPI description of the shelf is rendered")
    fun openApiRendered() {
        val resource = HeartResource(
            layers = layers,
            load = { error("not read") },
            perform = { shelf: Shelf, _, _ -> Acted(shelf) },
            find = { _, _ -> emptyList() },
        )
        openApi = ObjectMapper().readTree(OpenApiDocument(listOf(resource), version = "1.0.0").body)
    }

    @Then("its act operations are exactly")
    fun actOperations(table: DataTable) {
        val expected = table.asMaps().map { row ->
            listOf(row["method"], row["path"], row["operationId"], row["body"].orEmpty(), row["query"].orEmpty())
        }
        val actual = openApi.path("paths").properties().filter { "/actions/" in it.key }.flatMap { (path, item) ->
            item.properties().map { (method, operation) ->
                val body = operation.path("requestBody").path("content").fieldNames().asSequence().joinToString()
                val query = operation.path("parameters").filter { it.path("in").asText() == "query" }
                    .joinToString { it.path("name").asText() }
                listOf(method.uppercase(), path, operation.path("operationId").asText(), body, query)
            }
        }
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals(expected.size, actual.size, "$actual")
    }

    @Then("the shelf's {string} form's integrity is that of these members, canonical")
    fun integrityOf(key: String, table: DataTable) {
        val l0 = layers.layer1(Shelves.stocked(SHELF), Rung.L0)["_forms"] as Map<String, Map<String, Any?>>
        val embedded = (l0.getValue(key)["schema"] as Map<String, Any?>) - "\$id"
        val schema = layers.schemas.values.first { JsonTrees.plain(it) == embedded }
        val members = table.asMaps().associate { row ->
            val value = row.getValue("value")
            row.getValue("member") to when {
                value.startsWith("the integrity of") -> ContentAddress.integrity(schema)
                value.startsWith("{") -> JsonTrees.plain(value)
                else -> value
            }
        }
        setRendered()
        assertEquals(ContentAddress.integrity(ContentAddress.canonical(members)), forms.getValue(key)["hr:integrity"])
    }

    @When("a shelf act is declared on the {word} with the method {word} and a relation that is {word}")
    fun declared(where: String, method: String, alps: String) {
        val profile = Shelves.profile(tryIt = AlpsType.valueOf(alps.uppercase()))
        val act = Act("tryIt", "try-it", ContentAddress.canonicalText("{}"), method = method)
        refused = runCatching {
            if (where == "bookmark") {
                Shelves.type(profile, bookmarkActs = listOf(act))
            } else {
                Shelves.type(profile, listOf(act))
            }
        }.exceptionOrNull()?.message
    }

    @Then("the shelf's declaration is refused, saying {string}")
    fun declarationRefused(why: String) = assertEquals(why, refused)

    @When("the shelf act {string} is asked by DELETE with the query {string}")
    fun askedWithQuery(name: String, query: String) {
        input = ActRequests.input(type.act(name), null, query)
    }

    @Then("the act's input is {}")
    fun inputIs(json: String) = assertEquals(json, input)

    @When("the shelf act {string} is asked by {word}")
    fun askedBy(name: String, method: String) {
        act = type.act(name)
        asked = method
    }

    @Then("it is {}")
    fun outcome(said: String) {
        val act = checkNotNull(act)
        val allowed = ActRequests.allowed(act, asked)
        assertEquals(said, if (allowed) "allowed" else "not allowed, Allow: ${act.method}")
    }

    private fun target(key: String): Map<String, Any?> =
        (forms.getValue(key)["_links"] as Map<String, Map<String, Any?>>).getValue("target")

    private companion object {
        const val SHELF = 3L
    }
}
