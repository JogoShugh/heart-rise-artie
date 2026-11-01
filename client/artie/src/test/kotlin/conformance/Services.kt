package conformance

import heart.HeartRequest
import heart.HeartResponse
import heart.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI

const val BOOKMARK = "http://garden.test/"
const val FAKE = "http://fake.test/"

private const val PROFILE = "https://w3id.org/heart-rise/hal-schema-forms"
private const val NOT_RECORDED = 599
private const val CURIE = """{"name":"hr","href":"https://w3id.org/heart-rise/rels/{rel}","templated":true}"""

private fun pathOf(url: String) = URI(url).let { it.rawPath + (it.rawQuery?.let { query -> "?$query" } ?: "") }

/**
 * The garden as heart-ts recorded it, in fixtures/garden. Clients differ in
 * what they read and when, so this replay is looser than heart-ts's own: each
 * act must be the one recorded, in order, while a read may repeat one already
 * answered. An act that was never recorded gets a status no service sends.
 */
class GardenReplay(private val exchanges: List<JsonObject>) : Transport {
    private var next = 0

    private fun JsonObject.matches(request: HeartRequest): Boolean {
        val recorded = getValue("request").jsonObject
        fun text(name: String) = recorded[name]?.jsonPrimitive?.content
        return text("method") == request.method && text("url") == pathOf(request.url) &&
            text("authorized").toBoolean() == (request.header("Authorization") != null) &&
            (request.method == "GET" || text("ifMatch") == request.header("If-Match"))
    }

    override fun send(request: HeartRequest): HeartResponse {
        val ahead = (next until exchanges.size).firstOrNull { exchanges[it].matches(request) }
        val found = ahead ?: exchanges.indexOfFirst { request.method == "GET" && it.matches(request) }
        if (ahead != null) next = ahead + 1
        if (found < 0) return HeartResponse(NOT_RECORDED, emptyMap(), "not a recorded request")
        val response = exchanges[found].getValue("response").jsonObject
        val headers = response.getValue("headers").jsonObject.mapValues { listOf(it.value.jsonPrimitive.content) }
        return HeartResponse(response.getValue("status").jsonPrimitive.content.toInt(), headers, text(response))
    }

    private fun text(response: JsonObject) = response.getValue("body").jsonPrimitive.content

    companion object {
        private val fixtures = File(System.getProperty("conformance.fixtures"))

        fun forScenario(scenario: String): GardenReplay {
            val name = scenario.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val recorded = Json.parseToJsonElement(File(fixtures, "$name.json").readText())
            return GardenReplay(recorded.jsonArray.map { it.jsonObject })
        }
    }
}

/** The small services client.feature describes. */
object FakeServices {
    private val hal = mapOf("Content-Type" to listOf("application/hal+json"))
    private val profile = hal + mapOf("Link" to listOf("<$PROFILE>; rel=\"profile\""), "ETag" to listOf("\"1\""))

    private fun service(answer: (String) -> HeartResponse) = object : Transport {
        override fun send(request: HeartRequest) = answer(pathOf(request.url))
    }

    fun mismatchedSet(): Transport = service { path ->
        if (path == "/set") {
            HeartResponse(200, hal, """{"_forms":{"harvest":{"_links":{"target":{"href":"/x"}},"method":"POST"}}}""")
        } else {
            val link = """{"href":"/set","integrity":"sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}"""
            val applied = mapOf("Preference-Applied" to listOf("forms=link, schema=link"))
            val links = """{"self":{"href":"/"},"curies":[$CURIE],"hr:forms":$link}"""
            HeartResponse(200, profile + applied, """{"id":1,"_links":$links}""")
        }
    }

    fun embeddedAuthor(member: String): Transport = service { path ->
        if (path == "/users/ada") {
            val form = """{"_links":{"target":{"href":"/users/ada/actions/befriend"}},"method":"POST","schema":{}}"""
            val links = """{"self":{"href":"/users/ada"}}"""
            HeartResponse(200, profile, """{"login":"ada","name":"Ada Lovelace","_links":$links,"_forms":{"befriend":$form}}""")
        } else {
            val author = """{"login":"ada","_links":{"self":{"href":"/users/ada","title":"Ada"}}}"""
            val controls = """"_links":{"self":{"href":"/"}},"_forms":{},"_embedded":{"author":$author}"""
            HeartResponse(200, profile, """{"number":482,"$member":"kept as state",$controls}""")
        }
    }
}
