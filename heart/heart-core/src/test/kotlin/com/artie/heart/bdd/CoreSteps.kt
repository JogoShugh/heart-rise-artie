package com.artie.heart.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.Negotiation
import com.artie.heart.NotAcceptableException
import com.artie.heart.Rung
import com.artie.heart.JsonTrees
import com.artie.heart.StateETag
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import jakarta.json.Json
import jakarta.json.JsonArray
import jakarta.json.JsonNumber
import jakarta.json.JsonObject
import jakarta.json.JsonString
import jakarta.json.JsonValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.StringReader

class CoreSteps {
    private var tree: Any? = null
    private var body: String = ""
    private var accept: String = ""
    private var dialect: Result<Dialect>? = null
    private var rung: Rung? = null

    @Given("the JSON tree {}")
    fun theJsonTree(json: String) {
        tree = plain(Json.createReader(StringReader(json)).readValue())
    }

    @Then("its canonical form is {}")
    fun itsCanonicalFormIs(expected: String) {
        assertEquals(expected, ContentAddress.canonical(tree))
    }

    @Then("the JSON text {string} has the same canonical form")
    fun theJsonTextHasTheSameCanonicalForm(text: String) {
        assertEquals(ContentAddress.canonical(tree), ContentAddress.canonicalText(text))
    }

    @Given("the canonical body {}")
    fun theCanonicalBody(text: String) {
        body = text
    }

    @Then("its address is {string}")
    fun itsAddressIs(expected: String) = assertEquals(expected, ContentAddress.address(body))

    @Then("its integrity is {string}")
    fun itsIntegrityIs(expected: String) = assertEquals(expected, ContentAddress.integrity(body))

    @Then("its Repr-Digest is {string}")
    fun itsReprDigestIs(expected: String) = assertEquals(expected, ContentAddress.reprDigest(body))

    @When("^a client sends Accept \"([^\"]*)\" and Accept-Profile \"([^\"]*)\"$")
    fun aClientSendsAcceptAndProfile(accept: String, profile: String) {
        dialect = runCatching { Negotiation.dialect(accept.ifEmpty { null }, profile.ifEmpty { null }) }
    }

    @Then("the dialect is {word}")
    fun theDialectIs(expected: String) = assertEquals(Dialect.valueOf(expected), checkNotNull(dialect).getOrThrow())

    @Then("the dialect is refused as not acceptable")
    fun theDialectIsRefused() {
        assertTrue(checkNotNull(dialect).exceptionOrNull() is NotAcceptableException, "expected 406, got $dialect")
    }

    @When("^a client sends Accept \"([^\"]*)\"$")
    fun aClientSendsAccept(value: String) {
        accept = value
    }

    @Then("JSON Home is {}")
    fun jsonHomeIs(wanted: String) = assertEquals(wanted == "wanted", Negotiation.wantsJsonHome(accept))

    @Then("plain JSON is {}")
    fun plainJsonIs(wanted: String) =
        assertEquals(wanted == "wanted", Negotiation.wantsPlainJson(accept.ifEmpty { null }))

    @When("^a client sends Prefer \"(.*)\"$")
    fun aClientSendsPrefer(prefer: String) {
        rung = Rung.fromPrefer(prefer.ifEmpty { null })
    }

    @Then("the rung is {word}")
    fun theRungIs(expected: String) = assertEquals(Rung.valueOf(expected), rung)

    @Then("the validator of state {int} in dialect {word} at rung {word}, for the representation {}, is {string}")
    fun theValidatorIs(version: Int, dialect: String, rung: String, representation: String, expected: String) {
        val tree = JsonTrees.plain(representation)
        assertEquals("\"$expected\"", StateETag.of(version, Dialect.valueOf(dialect).etagTag, Rung.valueOf(rung), tree))
    }

    @Then("the validator of state {int} in dialect {word} at rung {word} differs between the representations {} and {}")
    fun theValidatorDiffers(version: Int, dialect: String, rung: String, first: String, second: String) {
        val tag = Dialect.valueOf(dialect).etagTag
        val one = StateETag.of(version, tag, Rung.valueOf(rung), JsonTrees.plain(first))
        val other = StateETag.of(version, tag, Rung.valueOf(rung), JsonTrees.plain(second))
        assertTrue(one != other, "both are $one")
    }

    @Then("^If-Match (.+) (matches|does not match) the current validator \"(.+)\"$")
    fun ifMatchAgainstCurrent(ifMatch: String, verdict: String, current: String) {
        assertEquals(verdict == "matches", StateETag.matches(ifMatch, "\"$current\""))
    }

    /** A JSON-P value as plain Kotlin maps, lists and scalars: the trees servers build. */
    private fun plain(value: JsonValue): Any? = when (value) {
        is JsonObject -> value.mapValues { plain(it.value) }
        is JsonArray -> value.map { plain(it) }
        is JsonString -> value.string
        is JsonNumber -> if (value.isIntegral) value.longValue() else value.doubleValue()
        JsonValue.TRUE -> true
        JsonValue.FALSE -> false
        else -> null
    }
}
