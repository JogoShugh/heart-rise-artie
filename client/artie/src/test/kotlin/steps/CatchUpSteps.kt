package steps

import com.artie.heart.gateway.fixtures.Pull
import heart.HeartRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertTrue

/** script-catch-up.feature: a pull request GitHub is still working out, read again before an act is refused. */
class CatchUpSteps(
    private val ctx: ClientContext,
    private val gateway: GitHubGatewaySteps,
    private val scripts: ScriptSteps,
) {
    @Given("{string} has pull request {long} on the small GitHub, open, titled {string}")
    fun pull(repository: String, number: Long, title: String) {
        val (owner, repo) = repository.split("/")
        gateway.github.pulls += Pull(owner, repo, number, "open", title)
    }

    @Given(
        "ARTIE guards the run {string}, pinning {string} as pull request {long} offers it " +
            "once GitHub has worked it out",
    )
    fun pinningSettled(guard: String, relation: String, number: Long) {
        val pull = read("/repos/acme/rockets/pulls/$number")
        val link = pull.getValue("_links").jsonObject.getValue("hr:forms").jsonObject
        val set = read(link.getValue("href").jsonPrimitive.content)
        val form = set.getValue("_forms").jsonObject.getValue(relation).jsonObject
        val bookmark = read("/").getValue("_forms").jsonObject.mapValues { (_, offered) ->
            offered.jsonObject.getValue("hr:integrity").jsonPrimitive.content to null
        }
        ctx.guard = guard
        ctx.pins = bookmark + (relation to (form.getValue("hr:integrity").jsonPrimitive.content to
            link.getValue("integrity").jsonPrimitive.content))
    }

    @Given(
        "GitHub is still working out whether pull request {long} can be merged, " +
            "and works it out once the gateway read it",
    )
    fun settles(number: Long) {
        pending(number)
        gateway.github.afterNextRead[number] = { it.mergeable = true; it.mergeableState = "clean" }
    }

    @Given("GitHub is still working out whether pull request {long} can be merged, and never works it out")
    fun pending(number: Long) {
        gateway.github.pulls.first { it.number == number }.apply { mergeable = null; mergeableState = "unknown" }
    }

    @Then("the run was stopped at step {int}, as not offered")
    fun stoppedNotOffered(step: Int) {
        val said = checkNotNull(scripts.stoppedMessage()) { "the run finished" }
        assertTrue(said.startsWith("stopped at step $step,") && "not offered" in said, said)
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
}
