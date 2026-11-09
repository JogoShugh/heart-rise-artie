package com.artie.heart.bdd

import com.artie.heart.Signatures
import com.artie.heart.Signed
import com.artie.heart.Signer
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.security.KeyPair
import java.security.KeyPairGenerator

/** The library signs a body; a checker verifies it against a body and a key (RFC 9421 over Ed25519). */
class SignedSteps {
    private val keys = mutableMapOf<String, KeyPair>()
    private var signed: Signed? = null
    private var accepted: Boolean? = null

    /** The key called [name], made once per scenario: `library` signs, and `other` is a key that did not. */
    private fun key(name: String): KeyPair =
        keys.getOrPut(name) { KeyPairGenerator.getInstance("Ed25519").generateKeyPair() }

    @Given("^the library signs the body (.*)$")
    fun librarySigns(body: String) {
        signed = Signer(key("library").private, KEY_ID).sign("PUT", TARGET, body, IF_MATCH, CREATED)
    }

    @When("^the signature is checked against the body (.*) with the key of (library|other)$")
    fun checked(body: String, name: String) {
        accepted = Signatures.verify(checkNotNull(signed) { "nothing was signed" }, body, key(name).public)
    }

    @Then("^the signature is (accepted|refused)$")
    fun outcome(expected: String) {
        assertEquals(expected == "accepted", checkNotNull(accepted) { "nothing was checked" })
    }

    private companion object {
        const val KEY_ID = "library-2026-11"
        const val TARGET = "https://caller.example/replies/r-7f3/completed"
        const val IF_MATCH = "\"v2\""
        const val CREATED = 1794153600L
    }
}
