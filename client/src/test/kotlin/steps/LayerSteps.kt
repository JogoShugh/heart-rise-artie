package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import heart.HeartResponse
import heart.Rung
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.security.MessageDigest
import java.util.Base64

/** Steps for SPEC §6.5-6.7: content-addressed layers, the rung, and the cache. */
class LayerSteps(private val ctx: ClientContext) {

    @Given("the client works at rung {word}")
    fun worksAt(rung: String) {
        ctx.rung = Rung.valueOf(rung)
    }

    @Then("the current representation is at rung {word}")
    fun currentAt(rung: String) = assertEquals(Rung.valueOf(rung), ctx.client.appliedRung)

    @Given("the service serves schema {string} at its content address:")
    fun servesSchema(name: String, yaml: String) = serveAddressed("schema", "/schemas", name, yaml)

    @Given("the service serves affordance set {string} at its content address:")
    fun servesSet(name: String, yaml: String) = serveAddressed("set", "/affordance-sets", name, yaml)

    @Given("the service serves affordance set {string} at {string}:")
    fun servesSetAt(name: String, path: String, yaml: String) = serve("set", name, path, json(yaml))

    @Given("the service serves affordance set {string} at {string} with Repr-Digest {string}:")
    fun servesSetWithDigest(name: String, path: String, digest: String, yaml: String) =
        serve("set", name, path, json(yaml), digest)

    @Given("the service serves affordance set {string} at {string} with its own Repr-Digest:")
    fun servesSetWithOwnDigest(name: String, path: String, yaml: String) {
        val body = json(yaml)
        serve("set", name, path, body, "sha-256=:${base64Sha256(body)}:")
    }

    /** A Layer 1 response at L2 whose only manifest is a link to [set] carrying [integrity]. */
    @Given("bed {int} links to affordance set {string} with integrity {string}")
    fun bedLinksWithIntegrity(bed: Int, set: String, integrity: String) =
        serveBed(bed, """{"href":"${ctx.expand("{set:$set}")}","integrity":"${ctx.expand(integrity)}"}""")

    @Given("bed {int} and bed {int} both link to affordance set {string}")
    fun bothLink(first: Int, second: Int, set: String) {
        bedLinks(first, set)
        bedLinks(second, set)
    }

    /** A Layer 1 response at L2 whose only manifest is the link to [set]. */
    @Given("bed {int} links to affordance set {string}")
    fun bedLinks(bed: Int, set: String) = serveBed(bed, """{"href":"${ctx.expand("{set:$set}")}"}""")

    private fun serveBed(bed: Int, link: String) {
        val body = """{"id":$bed,"_links":{"self":{"href":"/beds/$bed"},""" +
            """"https://github.com/jogoshugh/heart-rise/rels/forms":$link}}"""
        val headers = mapOf(
            "Content-Type" to listOf("application/hal+json"),
            "ETag" to listOf("\"seq-1.hsf.L2\""),
            "Link" to listOf("<https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel=\"profile\""),
        )
        ctx.transport.respond("GET", "http://garden.test/beds/$bed", HeartResponse(OK, headers, body))
    }

    @Given("the second client shares the client's cache")
    fun shareCache() {
        ctx.shareCache = true
    }

    @Then("the two clients hold the same affordance-set link")
    fun sameSetLink() {
        val mine = ctx.client.current?.formsLink
        assertTrue(mine != null && mine == ctx.second.current?.formsLink, "$mine vs ${ctx.second.current?.formsLink}")
    }

    @Then("the client sent these requests:")
    fun requests(expected: List<String>) =
        assertEquals(expected.map(ctx::expand), ctx.transport.requests.map { "${it.method} ${it.url}" })

    @Then("the last request had header {string} set to {string}")
    fun lastRequestHeader(name: String, value: String) =
        assertEquals(value, ctx.transport.requests.last().header(name))

    @Then("the cache holds {string}")
    fun cacheHolds(url: String) = assertTrue(ctx.cache.holds(ctx.expand(url)), "cache lacks $url")

    @Then("the cache holds nothing for {string}")
    fun cacheLacks(url: String) = assertFalse(ctx.cache.holds(ctx.expand(url)), "cache holds $url")

    /** Mints the address from the exact bytes served, the way the reference server names them (§6.5). */
    private fun serveAddressed(kind: String, prefix: String, name: String, yaml: String) {
        val body = json(yaml)
        val hash = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(ADDRESS_HEX)
        serve(kind, name, "$prefix/sha256-$hash", body)
    }

    /** Serves [body] at [path] as an immutable layer and records its address and integrity value. */
    private fun serve(kind: String, name: String, path: String, body: String, reprDigest: String? = null) {
        ctx.addresses["$kind:$name"] = path
        ctx.addresses["integrity:$kind:$name"] = "sha256-${base64Sha256(body)}"
        val headers = buildMap {
            put("Content-Type", listOf("application/json"))
            put("Cache-Control", listOf("public, max-age=31536000, immutable"))
            reprDigest?.let { put("Repr-Digest", listOf(it)) }
        }
        ctx.transport.respond("GET", "http://garden.test$path", HeartResponse(OK, headers, body))
    }

    private fun json(yaml: String): String = jsonMapper.writeValueAsString(yamlMapper.readTree(ctx.expand(yaml)))

    private fun base64Sha256(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private companion object {
        const val ADDRESS_HEX = 16
        const val OK = 200
        val yamlMapper = ObjectMapper(YAMLFactory())
        val jsonMapper = ObjectMapper()
    }
}
