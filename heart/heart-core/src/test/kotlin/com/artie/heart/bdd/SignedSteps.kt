package com.artie.heart.bdd

import com.artie.heart.Signatures
import com.artie.heart.Signed
import com.artie.heart.Signer
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Base64

/** The library signs a body; a checker verifies it against a body and a key (RFC 9421 over Ed25519). */
class SignedSteps {
    private val keys = mutableMapOf<String, KeyPair>()
    private var signed: Signed? = null
    private var accepted: Boolean? = null
    private var jwk: Map<String, String>? = null

    /** The key called [name], made once per scenario: `library` signs, and `other` is a key that did not. */
    private fun key(name: String): KeyPair =
        keys.getOrPut(name) { KeyPairGenerator.getInstance("Ed25519").generateKeyPair() }

    @Given("^the library signs the body (.*)$")
    fun librarySigns(body: String) {
        signed = Signer(key("library").private, KEY_ID).sign("PUT", TARGET, body, IF_MATCH, CREATED)
    }

    @When("^the signature is checked against the body (.*) with the key of (library|other)(, read back from its JWK)?$")
    fun checked(body: String, name: String, fromJwk: String?) {
        val key = key(name).public.let { if (fromJwk == null) it else Signatures.keyOf(Signatures.jwk(it, KEY_ID)) }
        accepted = Signatures.verify(checkNotNull(signed) { "nothing was signed" }, body, key)
    }

    @Then("^the signature is (accepted|refused)$")
    fun outcome(expected: String) {
        assertEquals(expected == "accepted", checkNotNull(accepted) { "nothing was checked" })
    }

    @When("the library's key is published as a JWK, with the key id {string}")
    fun published(keyId: String) {
        jwk = Signatures.jwk(key("library").public, keyId)
    }

    @Then("the JWK has these members")
    fun members(table: DataTable) {
        val expected = table.asMaps().single()
        assertEquals(expected, checkNotNull(jwk).filterKeys { it in expected })
    }

    @Then("its x is the key's 32 bytes, in base64url without padding")
    fun x() {
        val raw = Base64.getUrlDecoder().decode(checkNotNull(jwk).getValue("x"))
        assertEquals(KEY_BYTES, raw.size)
        assertEquals(raw.toList(), key("library").public.encoded.takeLast(KEY_BYTES))
    }

    private companion object {
        const val KEY_ID = "library-2026-11"
        const val TARGET = "https://caller.example/replies/r-7f3/completed"
        const val IF_MATCH = "\"v2\""
        const val CREATED = 1794153600L
        const val KEY_BYTES = 32
    }
}
