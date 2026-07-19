package heart

import ai.koog.agents.core.tools.Tool
import ai.koog.serialization.typeToken
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class HeartTool(
    private val httpClient: HttpClient, // Injected Ktor client
    val formId: String,
    val method: String,
    val targetUrl: String,
    val schema: JsonObject,
    val validator: JsonSchemaValidator
) : Tool<JsonObject, JsonObject>(
    argsType = typeToken<JsonObject>(),
    resultType = typeToken<JsonObject>(),
    descriptor = HalSchemaToDescriptorConverter.convert(formId, schema)
) {
    override suspend fun execute(args: JsonObject): JsonObject {
        validator.validate(args, schema)

        println("--- H.E.A.R.T. TRANSITION: $formId ---")

        return try {
            val response: HttpResponse = httpClient.request(targetUrl) {
                val upperMethod = this@HeartTool.method.uppercase()
                method = HttpMethod.parse(upperMethod)

                if (upperMethod == "GET") {
                    // 🔥 THE FIX: Map JSON keys to URL Query Parameters
                    url {
                        args.forEach { key, value ->
                            // Unwrap the JsonPrimitive (e.g., "jupiter" instead of "\"jupiter\"")
                            parameters.append(key, value.jsonPrimitive.content)
                        }
                    }
                } else {
                    // Keep POST/PUT logic as is
                    contentType(ContentType.Application.Json)
                    setBody(args)
                }
            }

            val responseBody = response.bodyAsText()
            Json.decodeFromString<JsonObject>(responseBody)
        } catch (e: Exception) {
            buildJsonObject {
                put("status", "error")
                put("message", e.localizedMessage ?: "Network error")
            }
        } finally {
            println("--- TRANSITION COMPLETE ---")
        }
    }
}