//import heart.HeartSchemaValidator
//import heart.HeartTool
//import heart.UniformArgs
//import heart.heartHttpClient
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.assertThrows
//import kotlinx.coroutines.runBlocking
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.*
//import kotlin.test.assertEquals
//
//val client = heartHttpClient()
//
//// Concrete Data Class
//@Serializable
//data class SowSeedRequest(
//    val plantCultivar: String,
//    val count: Int
//)
//
//// Concrete Schema Definition
//val SowSeedSchema = buildJsonObject {
//    put("title", "Sow Seed Form")
//    putJsonObject("properties") {
//        putJsonObject("plantCultivar") { put("type", "string") }
//        putJsonObject("count") { put("type", "integer") }
//    }
//    putJsonArray("required") {
//        add("plantCultivar")
//        add("count")
//    }
//}
//
//class HeartToolTests {
//    private lateinit var tool: HeartTool
//
//    @BeforeEach
//    fun setup() {
//        tool = HeartTool(
//            httpClient = client,
//            formId = "sowSeed",
//            schema = SowSeedSchema,
//            validator = HeartSchemaValidator(),
//            method = "POST",
//            targetUrl = "do not know"
//        )
//    }
//
//    @Test
//    fun `descriptor built from schema`() {
//        assertEquals("sowSeed", tool.descriptor.name)
//        assertEquals(2, tool.descriptor.requiredParameters.size)
//        assertEquals("plantCultivar", tool.descriptor.requiredParameters[0].name)
//        assertEquals("count", tool.descriptor.requiredParameters[1].name)
//    }
//
//    @Test
//    fun `execute with concrete data class returns success`() = runBlocking {
//        val result = tool.execute(SowSeedRequest(plantCultivar = "Kale", count = 5).toUniformArgs())
//        assertEquals("success", result.data["status"]?.jsonPrimitive?.content)
//    }
//
//    @Test
//    fun `execute with invalid payload throws exception`() {
//        val invalidArgs = UniformArgs(buildJsonObject {
//            put("plantCultivar", "Kale")
//        })
//
//        assertThrows<IllegalArgumentException> {
//            runBlocking { tool.execute(invalidArgs) }
//        }
//    }
//}

import heart.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

// Concrete Data Class
@Serializable
data class SowSeedRequest(
    val plantCultivar: String,
    val count: Int
)

// Extension to match your test style
fun SowSeedRequest.toUniformArgs() = UniformArgs(
    Json.encodeToJsonElement(this).jsonObject
)

class HeartToolTests {
    private lateinit var tool: HeartTool
    private lateinit var mockClient: HttpClient

    // Concrete Schema Definition
    private val SowSeedSchema = buildJsonObject {
        put("title", "Sow Seed Form")
        putJsonObject("properties") {
            putJsonObject("plantCultivar") { put("type", "string") }
            putJsonObject("count") { put("type", "integer") }
        }
        putJsonArray("required") {
            add("plantCultivar")
            add("count")
        }
    }

    @BeforeEach
    fun setup() {
        // Setup the MockEngine to simulate the Garden Server
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"status":"success", "message":"Transition recorded"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        tool = HeartTool(
            httpClient = mockClient,
            formId = "sowSeed",
            schema = SowSeedSchema,
            validator = HeartSchemaValidator(),
            method = "POST",
            targetUrl = "https://starborn.ag/api/beds/1/sow"
        )
    }

    @Test
    fun `descriptor built from schema`() {
        // This tests the HAL -> Agent Metadata conversion logic
        assertEquals("sowSeed", tool.descriptor.name)
        assertEquals(2, tool.descriptor.requiredParameters.size)
        val params = tool.descriptor.requiredParameters.map { it.name }
        assert(params.contains("plantCultivar"))
        assert(params.contains("count"))
    }

    @Test
    fun `execute with concrete data class returns success`() = runBlocking {
        // This now tests the FULL pipeline: Validation -> Ktor -> Response Mapping
        val result = tool.execute(SowSeedRequest(plantCultivar = "Kale", count = 5).toUniformArgs())
        assertEquals("success", result.data["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `execute with invalid payload throws exception`() {
        // This tests that the validator catches LLM hallucination before the network call
        val invalidArgs = UniformArgs(buildJsonObject {
            put("plantCultivar", "Kale")
            // missing "count"
        })

        assertThrows<IllegalArgumentException> {
            runBlocking { tool.execute(invalidArgs) }
        }
    }
}