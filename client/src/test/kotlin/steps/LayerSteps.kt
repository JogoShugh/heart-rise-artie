package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.HeartResponse
import heart.Rung
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.security.MessageDigest

/** Steps for SPEC §6.5-6.7: content-addressed layers, the rung, and the cache. */
class LayerSteps(private val ctx: ClientContext) {

    @Given("the client works at rung {word}")
    fun worksAt(rung: String) {
        ctx.rung = Rung.valueOf(rung)
    }

    @Given("the service serves schema {string} at its content address:")
    fun servesSchema(name: String, yaml: String) = serveAddressed("schema", "/schemas", name, yaml)

    @Given("the service serves affordance set {string} at its content address:")
    fun servesSet(name: String, yaml: String) = serveAddressed("set", "/affordance-sets", name, yaml)

    @Then("the client sent these requests:")
    fun requests(expected: List<String>) =
        assertEquals(expected.map(ctx::expand), ctx.transport.requests.map { "${it.method} ${it.url}" })

    @Then("the last request had header {string} set to {string}")
    fun lastRequestHeader(name: String, value: String) =
        assertEquals(value, ctx.transport.requests.last().header(name))

    @Then("the cache holds nothing for {string}")
    fun cacheLacks(url: String) = assertFalse(ctx.cache.holds(url), "cache holds $url")

    /** Mints the address from the exact bytes served, the way a server must (§6.5). */
    private fun serveAddressed(kind: String, prefix: String, name: String, yaml: String) {
        val body = jsonMapper.writeValueAsString(yamlMapper.readTree(ctx.expand(yaml)))
        val hash = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(ADDRESS_HEX)
        val path = "$prefix/sha256-$hash"
        ctx.addresses["$kind:$name"] = path
        val headers = mapOf(
            "Content-Type" to listOf("application/json"),
            "Cache-Control" to listOf("public, max-age=31536000, immutable"),
        )
        ctx.transport.respond("GET", "http://garden.test$path", HeartResponse(OK, headers, body))
    }

    private companion object {
        const val ADDRESS_HEX = 16
        const val OK = 200
        val yamlMapper = ObjectMapper(YAMLFactory())
        val jsonMapper = ObjectMapper()
    }
}
