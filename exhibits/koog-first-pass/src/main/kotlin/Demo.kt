import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import heart.HeartSchemaValidator
import heart.HeartTool
import heart.heartHttpClient
import heart.update
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI

val client = heartHttpClient()

val registry = ToolRegistry {
    tool(::askUser)
    tool(::fetchApiState)
    tool(::executeAction)
}

@Tool
@LLMDescription("Ask the user a question by sending it to stdout and return the answer from stdin")
fun askUser(
    @LLMDescription("Question from the agent to the user") question: String
): String {
    println("\n🤖 Agent asks: $question")
    print("👤 You: ")
    return readln()
}

@Tool
@LLMDescription("Fetches the current state and available actions (HAL Schema Forms) for a given system URL.")
suspend fun fetchApiState(
    @LLMDescription("The URL of the API endpoint to fetch") url: String
): String {
    println("🌐 [System] Fetching state from: $url")

    return try {
        // client is the Ktor HttpClient in scope
        val response: HttpResponse = client.get(url) {
            header(HttpHeaders.Accept, "application/hal+json")
        }

        // Returns the body as a raw String for the Agent to parse
        response.bodyAsText()
    } catch (e: Exception) {
        // Return a JSON error so the LLM doesn't choke on a stack trace
        buildJsonObject {
            put("error", "Failed to fetch URL: ${e.message}")
        }.toString()
    }
}

@Tool
@LLMDescription("Executes an HTTP request against a remote system.")
suspend fun executeAction(
    @LLMDescription("The HTTP method. Either GET or POST.") method: String,
    @LLMDescription("The target URL for the action") url: String,
    @LLMDescription("The strict JSON payload matching the schema. If method is GET, this JSON will be converted to query parameters.") payload: String
): String {
    println("🚀 [System] Executing $method to $url")

    return try {
        // Parse the payload once
        val jsonPayload = if (payload.isNotBlank()) Json.parseToJsonElement(payload).jsonObject else emptyMap()

        val response: HttpResponse = client.request(url) {
            this.method = HttpMethod.parse(method.uppercase())

            if (this.method == HttpMethod.Get) {
                // Ktor automatically handles encoding and URL formatting
                url {
                    jsonPayload.forEach { (key, value) ->
                        parameters.append(key, value.jsonPrimitive.content)
                    }
                }
            } else {
                contentType(ContentType.Application.Json)
                setBody(payload) // Shove the raw string/json in
            }
        }

        println("📥 [System] Response Code: ${response.status.value}")
        response.bodyAsText()
    } catch (e: Exception) {
        buildJsonObject {
            put("error", "Execution failed: ${e.message}")
        }.toString()
    }
}

fun parseHalForms(jsonString: String, currentUrl: String): List<HeartTool> {
    // 1. Initial parse
    val validator = HeartSchemaValidator()
    val element = Json.parseToJsonElement(jsonString)

    // 2. If the 'content' was a string-encoded JSON, we need to extract the string and parse again
    val json = if (element is JsonPrimitive && element.isString) {
        Json.parseToJsonElement(element.content).jsonObject
    } else {
        element.jsonObject
    }
    val forms = json["_forms"]?.jsonObject ?: return emptyList()
    val baseUri = URI.create(currentUrl)

    return forms.map { (formId, formData) ->
        val formObj = formData.jsonObject
        val method = formObj["method"]?.jsonPrimitive?.content ?: "POST"
        val rawHref = formObj["_links"]?.jsonObject?.get("target")?.jsonObject?.get("href")?.jsonPrimitive?.content
            ?: ""

        // Use URI.resolve to handle relative vs absolute paths correctly
        val targetUrl = baseUri.resolve(rawHref).toString()

        HeartTool(
            httpClient = client,
            formId = formId,
            method = method,
            targetUrl = targetUrl,
            schema = formObj["schema"]?.jsonObject ?: buildJsonObject {},
            validator = validator
        )
    }
}

fun heartStrategy(
    registry: ToolRegistry,
    parallelTools: Boolean = false
): AIAgentGraphStrategy<String, String> = strategy("hateoas_sequential") {
    val nodeCallLLM by nodeLLMRequestMultiple()
    val nodeExecuteTool by nodeExecuteMultipleTools(parallelTools = parallelTools)
    val nodeSendToolResult by nodeLLMSendMultipleToolResults()
    val nodeRefreshRegistry by node<List<ReceivedToolResult>, List<ReceivedToolResult>>("RegistryRefresher") { results ->
        results.forEach { result ->
            // 1. THE LOGGING (Your lost treasure)
            println("\n--- [FLIGHT RECORDER] ---")
            println("Tool Executed: ${result.tool}")
            println("Arguments:     ${result.toolArgs}")
            println("Output State:  ${result.content.take(1000)}...") // Peek at the JSON
            println("-------------------------\n")
            val newState = result.content
            if (newState.contains("_forms")) {
                println("🔄 [H.E.A.R.T.] Re-wiring Registry based on new state...")

                // 1. Generate the domain-specific tools
                val newTools = parseHalForms(newState, "http://localhost:3000")

                // 2. ALWAYS keep the 'askUser' and 'fetch' tools available
                val allTools = ToolRegistry {
                    tool(::askUser)
                    tool(::fetchApiState)
                    newTools.forEach { it ->
                        tool(it)
                    }
                }

                // 3. Hot-swap the registry
                // Note: Make sure to include your basic tools so ARTIE can still talk/fetch
                registry.update(allTools.tools)
            }
        }
        results
    }

    edge(nodeStart forwardTo nodeCallLLM)

    edge(nodeCallLLM forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(
        nodeCallLLM forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString("\n") { m -> m.content } }
    )

    edge(nodeExecuteTool forwardTo nodeRefreshRegistry)

    edge(nodeRefreshRegistry forwardTo nodeSendToolResult)

    edge(nodeSendToolResult forwardTo nodeExecuteTool onMultipleToolCalls { true })
    edge(
        nodeSendToolResult forwardTo nodeFinish
                onMultipleAssistantMessages { true }
                transformed { it.joinToString("\n") { m -> m.content } }
    )
}
// 3. THE AGENT FACTORY

fun buildAgent(): AIAgent<String, String> {
    val apiKey = System.getenv("GEMINI_API_KEY")
        ?: throw IllegalArgumentException("Missing GEMINI_API_KEY environment variable")

    return AIAgent(
        promptExecutor = simpleGoogleAIExecutor(apiKey),
        systemPrompt = """
                # Persona
                You are ARTIE (Autonomous REST Traversal & Interaction Engine). 
                Your ONLY contract with reality is the HAL Schema Forms protocol.
                
                # Core Loop
                1. Discover: Use `fetchApiState` to read the HAL document at the user's requested URL.
                2. Analyze: Look at the `_forms` block to see what actions are currently available. Look at `_links` to see where you can navigate.
                3. Evaluate: Match the user's intent to an available action or navigation link. Read its JSON Schema.
                4. Gather: If the user did not provide all required fields defined in a form's schema, use `askUser` to prompt them for the exact missing data.
                5. Execute: Once you have all data, construct the JSON payload and use `executeAction` to fire it at the target `href`.
                6. Verify: The output of `executeAction` may contain new links or forms. Continue exploring if the user's intent is not fully resolved.
                7. Report: Summarize the final outcome to the user. You MUST explicitly list the NEW state and the newly available actions (forms) discovered from the final fetch so the user knows what they can do next. If the response caontains only a self rel, then you MUST follow that and use its response for this summary of what can be done next
                
                # Constraints
                - You MUST NOT guess URLs. You may only interact with URLs explicitly provided in `_links` or `_forms.href`.
                - You cannot execute an action if it is not explicitly listed in the `_forms` block of the current state.
                - If a schema requires a field you don't know, you MUST ask the user.
            """.trimIndent(),
        llmModel = GoogleModels.Gemini3_Flash_Preview,
        toolRegistry = registry,
        strategy = heartStrategy(registry),
        temperature = 0.1
    ) {
        install(ChatMemory)
    }
}

fun main() = runBlocking {
    println("==================================================")
    println(" ARTIE (H.E.A.R.T. Protocol) Initialized")
    println("==================================================")

    val agent = buildAgent()
    println(agent)
    val sessionId = "artie-session-01" // Used to key the memory

    val initialGoal = """
            Go to http://localhost:3000/.
            Find what's available at the root URL and summarize it to the user, then ask for their request.
        """.trimIndent()

    println("\n[Sending Initial Goal to Agent]")
    println(initialGoal)
    println("--------------------------------------------------\n")

    // 1. Kick off the conversation
    // Koog automatically creates the session history and saves the turn
    var result = agent.run(initialGoal, sessionId)
    println("\n✅ ARTIE:\n$result")

    // 2. The Clean Interactive Loop
    while (true) {
        print("\n👤 You: ")
        val userInput = readln()

        if (userInput.equals("quit", ignoreCase = true) || userInput.equals("exit", ignoreCase = true)) {
            println("ARTIE shutting down. Goodbye!")
            break
        }

        // 3. We just pass the NEW input along with the same session ID.
        // Koog automatically pulls the structured history (including all tool JSON payloads!),
        // appends the new message, and sends it to Gemini.
        result = agent.run(userInput, sessionId)
        println("\n✅ ARTIE:\n$result")
    }
}