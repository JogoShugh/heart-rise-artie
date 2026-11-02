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

private const val NOT_RECORDED = 599

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
