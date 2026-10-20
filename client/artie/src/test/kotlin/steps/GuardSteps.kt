package steps

import heart.HeartRequest
import heart.script.Run
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** script-guard.feature: a lock file, made from what the gateway offers now, or changed on purpose. */
class GuardSteps(private val ctx: ClientContext, private val scripts: ScriptSteps) {
    /** The set the issue's forms were offered in before the run, read when the lock file was made. */
    private var offeredSet: String? = null

    @Given("ARTIE guards the run {string}, pinning {string} to its form {string} in a set {string}")
    fun pinning(guard: String, relation: String, form: String, set: String) {
        val (formNow, setNow) = offeredNow(relation)
        offeredSet = setNow
        ctx.guard = guard
        val pinnedForm = if (form == "changed") CHANGED else formNow
        ctx.pins = bookmark() + (relation to (pinnedForm to if (set == "changed") CHANGED else setNow))
    }

    @Given("ARTIE guards the run {string}, pinning everything it offers but {string}")
    fun pinningAllBut(guard: String, relation: String) {
        ctx.guard = guard
        ctx.pins = bookmark() - relation
    }

    @Given("ARTIE guards the run {string}, pinning the link {string} {}")
    fun pinningLink(guard: String, relation: String, pin: String) {
        ctx.guard = guard
        ctx.pins = bookmark()
        ctx.linkPins = when (pin) {
            "not at all" -> emptyMap()
            "as offered" -> mapOf(relation to "")
            else -> mapOf(relation to pin.removePrefix("to ").trim('"'))
        }
    }

    /** The bookmark's forms, pinned as offered now: offered inline, by the bookmark, in no set. */
    private fun bookmark(): Map<String, Pair<String, String?>> =
        read("/").getValue("_forms").jsonObject.mapValues { (_, form) ->
            form.jsonObject.getValue("hr:integrity").jsonPrimitive.content to null
        }

    @Then("the run finished")
    fun finished() = assertEquals(null, scripts.stoppedMessage(), "the run stopped")

    @Then("the run was stopped at step {int} by its guard")
    fun stoppedByGuard(step: Int) {
        val said = checkNotNull(scripts.stoppedMessage()) { "the run finished" }
        assertTrue(said.startsWith("stopped at step $step,") && "guard, refused by the client" in said, said)
    }

    @Then("the run was stopped at step {int} by its guard, saying {string}")
    fun stoppedByGuardSaying(step: Int, why: String) {
        stoppedByGuard(step)
        val said = scripts.stoppedMessage().orEmpty()
        assertTrue(said.endsWith(": \"$why\""), said)
    }

    @Then("the run finished, step {int} marked unpinned")
    fun unpinned(step: Int) {
        finished()
        val steps = (Json.parseToJsonElement(Run.current.traceJson()).jsonObject.getValue("steps")
            as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
        val guard = steps[step - 1]["guard"] as? JsonObject
        assertEquals("true", guard?.get("unpinned")?.jsonPrimitive?.content, "guard: $guard")
    }

    @Then("the run finished, step {int} warned that the set holding {string} is not the one pinned")
    fun warned(step: Int, relation: String) {
        finished()
        val steps = (Json.parseToJsonElement(Run.current.traceJson()).jsonObject.getValue("steps")
            as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
        val guard = steps[step - 1]["guard"] as? JsonObject
        assertEquals(
            "the set holding $relation changed: pinned $CHANGED, offered $offeredSet",
            guard?.get("warning")?.jsonPrimitive?.content,
            "guard: $guard",
        )
    }

    /** The integrity of [relation]'s form, and of its set, as the gateway offers them on issue 10 now, at L2. */
    private fun offeredNow(relation: String): Pair<String, String> {
        val issue = read("/repos/acme/rockets/issues/10")
        val link = issue.getValue("_links").jsonObject.getValue("hr:forms").jsonObject
        val set = read(link.getValue("href").jsonPrimitive.content)
        val form = set.getValue("_forms").jsonObject.getValue(relation).jsonObject
        return form.getValue("hr:integrity").jsonPrimitive.content to link.getValue("integrity").jsonPrimitive.content
    }

    private fun read(path: String): JsonObject {
        val url = if (path.startsWith("http")) path else "https://github.gateway.test$path"
        val headers = mapOf(
            "Authorization" to "Bearer ada-token", "Accept" to "application/hal+json",
            "Prefer" to "forms=link, schema=link", "Accept-Profile" to "<https://w3id.org/heart-rise/hal-schema-forms>",
        )
        val body = checkNotNull(ctx.inProcess).send(HeartRequest("GET", url, headers)).body
        return Json.parseToJsonElement(body).jsonObject
    }

    private companion object {
        /** An integrity no form or set has: what a lock file holds once the server changed. */
        const val CHANGED = "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
