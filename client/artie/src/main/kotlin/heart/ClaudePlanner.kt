package heart

import heart.script.Move
import heart.script.PlanView
import heart.script.Planner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A goal run's planner on the Claude Messages API, through ARTIE's own Transport (spec/PLAN-PLAIN-TEXT-RUN.md).
 * As the Reason step's prompt: the tool and the system prompt never change, a cached prefix; what changes is the
 * last user message: the goal sentence by sentence, each marked done or not, the inputs, the moves made, the path
 * of resources the run is in, and what is held (its state, the forms it offers, its links). An answer in words
 * alone is a question for the person, never a move. It chooses no form and no value: each act's words go to the
 * reasoner, as a reason step's do.
 */
class ClaudePlanner(
    private val transport: Transport,
    private val apiKey: String,
    private val model: String = ClaudeReasoner.DEFAULT_MODEL,
    /** What its calls took, counted with its reasoner's when it has one. */
    val usage: ModelUsage = ModelUsage(),
) : Planner {

    override fun next(view: PlanView): Move {
        val response = transport.send(
            HeartRequest(
                "POST",
                ClaudeReasoner.ENDPOINT,
                mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION, "Content-Type" to "application/json"),
                request(view).toString(),
            ),
        )
        check(response.status in SUCCESS) {
            "Claude API answered ${response.status}: ${response.body.take(ERROR_EXCERPT)}"
        }
        val message = Json.parseToJsonElement(response.body).jsonObject
        usage.add(message["usage"] as? JsonObject)
        return move(message)
    }

    private fun request(view: PlanView): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        // A goal in steps offers only the moves the step now allows: a cached prefix for each kind of step.
        putJsonArray("tools") { add(tool(view.allowed.toList().ifEmpty { MOVES })) }
        putJsonObject("tool_choice") { put("type", "auto") }
        putJsonArray("system") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", SYSTEM)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                },
            )
        }
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", turn(view)) })
                    }
                },
            )
        }
    }

    /**
     * What changes from move to move, in tagged parts: the goal by sentence, or, for a goal in steps, its context,
     * its steps with the one now marked, and the resources they named.
     */
    internal fun turn(view: PlanView): String {
        val goal = if (view.steps.isEmpty()) {
            "<goal>\n" + view.sentences.withIndex().joinToString("\n") { (i, sentence) ->
                "${i + 1}. [${if (i + 1 in view.done) "done" else " "}] $sentence"
            } + "\n</goal>\n"
        } else {
            "<context>\n${view.goal}\n</context>\n<steps>\n${view.steps.joinToString("\n")}\n</steps>\n" +
                "<now>\n${view.current}\n</now>\n<named>\n" +
                view.named.entries.joinToString("\n") { (name, address) -> "$name = $address" } + "\n</named>\n"
        }
        val inputs = view.inputs.entries.joinToString("\n") { (name, value) -> "\$$name = $value" }
        val held = view.held
        // Only the links the client can follow: a model shown the others chases them.
        val links = held.followable.entries.joinToString("\n") { (rel, titles) ->
            titles.joinToString("\n") { title -> "$rel" + (title?.let { " \"$it\"" } ?: "") }
        }
        val state = held.state.toString().let { if (it.length > STATE_LIMIT) it.take(STATE_LIMIT) + "…" else it }
        val answers = view.answers.joinToString("\n") { (q, a) -> "Q: $q\nA: $a" }
        return "$goal<inputs>\n$inputs\n</inputs>\n" +
            "<moves>\n${view.moves.joinToString("\n")}\n</moves>\n<path>\n${view.path.joinToString("\n")}\n</path>\n" +
            "<held>\n${held.address}\n<state>\n$state\n</state>\n<forms>\n${forms(view)}" +
            "\n</forms>\n<links>\n$links\n</links>\n</held>\n<answers>\n$answers\n</answers>"
    }

    /** Each form held offers, a line each: its name, and what it is for where its profile says. */
    private fun forms(view: PlanView): String = view.held.offered.sorted().joinToString("\n") { name ->
        name + (view.described[name]?.let { " — ${it.lines().first()}" } ?: "")
    }

    private fun move(message: JsonObject): Move {
        val content = message.getValue("content").jsonArray.map { it.jsonObject }
        val call = content.firstOrNull { it["type"]?.jsonPrimitive?.content == "tool_use" }
            ?: return Move.Clarify(ClaudeReasoner.words(content))
        val input = call.getValue("input").jsonObject
        fun text(name: String) = input[name]?.jsonPrimitive?.content
        val sentence = input["sentence"]?.jsonPrimitive?.intOrNull
        val why = text("why")
        return when (text("move")) {
            "act" -> Move.Act(
                text("words").orEmpty(), sentence, input["opens"]?.jsonPrimitive?.booleanOrNull == true, why,
            )
            "back" -> Move.Back(text("to").orEmpty(), why)
            "wait" -> Move.Wait(text("relation").orEmpty(), reason = why)
            "check" -> Move.Check((input["facts"] as? JsonObject).orEmpty().mapValues { (_, v) -> plain(v) }, why)
            "done" -> Move.Done(sentence ?: 0, why)
            "finish" -> Move.Finish
            "stop" -> Move.Stop(text("why") ?: "the planner stopped")
            else -> Move.Clarify(text("question") ?: "Could you say more?")
        }
    }

    private fun plain(value: kotlinx.serialization.json.JsonElement): Any? {
        val primitive = value as? JsonPrimitive ?: return value.toString()
        return if (primitive.isString) primitive.content else
            primitive.booleanOrNull ?: primitive.longOrNull ?: primitive.doubleOrNull
    }

    companion object {
        private const val API_VERSION = "2023-06-01"
        private const val MAX_TOKENS = 1024
        private const val ERROR_EXCERPT = 300
        private const val STATE_LIMIT = 4000
        private val SUCCESS = 200..299
        private val MOVES = listOf("act", "back", "wait", "check", "done", "finish", "stop", "clarify")

        private val SYSTEM = """
            You plan a task for a user against a hypermedia service, one move at a time. The service offers,
            at each resource, the actions allowed now (forms) and its links. A separate step carries out each
            act: it reads your words, chooses the form and fills its fields. You choose what to do next.
            The user message has parts: <goal> is the task, numbered by sentence, each marked [done] or [ ];
            <inputs> are values the user gave, by name; <moves> are the moves made so far; <path> is the
            resources the run is in, the start first and the one held last; <held> is the resource held now:
            its address, <state>, the <forms> it offers by name, and its <links>; <answers> are the user's
            answers to your questions.
            Call the move tool exactly once, with one of these moves:
            - "act": words for one act at the resource held, as a person would ask it, with every value the
              act needs (for example "comment on it: Fixed in 1.2 by #132."). Write a value that is one of the
              inputs as ${'$'}name, never its value. Give the number of the sentence it serves. Set opens to
              true when you will make more moves at what the act leads to, or will come back here after it
              (an act that makes a new resource, such as a comment, leads to that resource).
            - "back": to a resource on the <path>, by its address, closing what was opened since.
            - "wait": until the resource held offers a form, before acting on it. Give the form's exact name,
              as in <forms> or as it will be named there, in relation (for example "github:merge").
            - "check": facts the goal asks to make sure of, each a member of <state> and the value it must
              have.
            - "done": a sentence is fully done, by its number. Mark each sentence done once it is.
            - "finish": every sentence is done.
            - "stop": the goal can't be done here, saying why.
            - "clarify": one short question for the user, when the goal and what is offered leave the next
              move open, or a value is missing. Never guess.
            Only act on forms in <forms>. Do each sentence once, in order where order matters.
            A move in <moves> marked refused changed nothing: try another way. The start, first on <path>,
            often offers forms that go straight to a resource by its names or numbers (a repository, a branch,
            an issue): go back to it when the resource held doesn't lead where you need.
            A goal in steps comes as <context>, the user's words around the steps; <steps>, the steps so far as
            a tree: [done] ones, [in] the steps the one now is inside, and [now], with how many sub-steps come
            after it; <now>, the step to do now; and <named>, resources earlier steps named, each at its
            address. Do only the step now: for an intent, act (as many times as it
            takes) and then "done"; for "Wait: …", one "wait"; for "Verify: …", one "check" of its facts. The
            runner moves from step to step: in a goal in steps every act leads on to what it led to (opens
            needn't be set), the step's sub-steps run where the run is when it is done, and after the step the
            runner comes back to where it began by itself: never "finish" in a goal in steps. For a step with sub-steps, act only to
            reach the resource its sub-steps start from (an act that opens), then "done": never do a sub-step's
            work early, since each comes next as a step of its own. Each act in <moves> says what was done:
            "acted" a form, or "followed" a link, and what came of it. A follow moves you and makes nothing:
            from a list, follow on to the item you need (a branch, an issue), then act there. Say "done" only
            once a move did the step's work, never because a follow came back done. "back" goes to a resource on <path> this
            step opened, or to where it began, no further.
        """.trimIndent()

        /** The move tool, offering [moves]. */
        private fun tool(moves: List<String>): JsonObject = buildJsonObject {
            put("name", "move")
            put(
                "description",
                "Make the next move of the task: one act, back, wait, check, done, finish, stop or a question.",
            )
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    // The reason first: said before the move, it is what the move follows from.
                    putJsonObject("why") {
                        put("type", "string")
                        put("description", "Why this move, in one sentence, from what is held and the step now.")
                    }
                    putJsonObject("move") {
                        put("type", "string")
                        put("enum", JsonArray(moves.map(::JsonPrimitive)))
                    }
                    putJsonObject("words") { put("type", "string") }
                    putJsonObject("sentence") { put("type", "integer") }
                    putJsonObject("opens") { put("type", "boolean") }
                    putJsonObject("to") { put("type", "string") }
                    putJsonObject("relation") { put("type", "string") }
                    putJsonObject("facts") { put("type", "object") }
                    putJsonObject("question") { put("type", "string") }
                }
                put("required", JsonArray(listOf(JsonPrimitive("why"), JsonPrimitive("move"))))
            }
            putJsonObject("cache_control") { put("type", "ephemeral") }
        }
    }
}
