package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/*
 * Scripts a party hands over by link, and the scenarios they are made of (linked-scripts.feature): a build links the
 * smoke test it archived; the release runs it on the preview the build deployed, and goes on by its outcome.
 */

/** One scenario's outcome: its [name], whether it [passed], and [why] not. */
class Scenario(val name: String, val passed: Boolean, val why: String? = null)

/**
 * Run [block] as the scenario [name]: a check that fails in it, or a refusal, fails the scenario, not the script,
 * which goes on from where the scenario began. The run keeps each scenario's outcome.
 */
fun Here.scenario(name: String, block: Here.() -> Unit) {
    val from = held.address
    val outcome = try {
        block()
        Scenario(name, true)
    } catch (stopped: GoalStopped) {
        throw stopped
    } catch (stopped: ScriptStopped) {
        Scenario(name, false, stopped.reason.removeSurrounding("\""))
    }
    run.scenarios += outcome
    // A scenario that failed inside a step's block left the client where it failed, whatever is held here: read
    // again where the scenario began, so the next one starts there, client and script alike.
    if (!outcome.passed || held.address != from) held = arrived(run.step, "\"scenario\"", navigator.recall(from))
}

/**
 * Read the kept script the link [rel] titled [title] leads to, and run it with [inputs], in a run of its own on the
 * same client; keep its outcome as [named]: whether every scenario `passed`, those that `failed`, by name, each
 * scenario, and where it `stopped`, if it did outside one. Then back here, read again.
 */
fun Here.runScript(rel: String, inputs: Map<String, String>, named: String, title: String? = null) {
    val step = ++run.step
    val script = linkedScript(step, rel, title)
    val child = Run(inputs = inputs, navigator = run.navigator, pause = run.pause).also {
        it.replies = run.replies
        it.clock = run.clock
        it.time = run.time
    }
    val outer = Run.current
    Run.current = child
    val stopped = try {
        runData(script)
        null
    } catch (stop: GoalStopped) {
        throw stop
    } catch (stop: ScriptStopped) {
        stop
    } finally {
        Run.current = outer
    }
    run.trace += child.trace
    run.notes += child.notes
    run.kept[named] = Run.Kept(held.address, State(outcome(child.scenarios, stopped)), inPlace = true)
    held = arrived(step, "\"$rel\"", navigator.recall(held.address))
}

/** The script held as data the link [rel] titled [title] leads to; the run stops at [step] when there is none. */
private fun Here.linkedScript(step: Int, rel: String, title: String?): Map<*, *> {
    fun stopped(why: String): Nothing = throw ScriptStopped(step, "\"$rel\"", "\"$why\"")
    val received = Reads.of(navigator).read(rel, title) ?: stopped("no $rel link to read a script from")
    if (received.status !in FIRST_OK..LAST_OK) stopped("the script the link $rel leads to: ${received.status}")
    return runCatching { org.yaml.snakeyaml.Yaml().load<Any?>(received.body.orEmpty()) as Map<*, *> }
        .getOrElse { stopped("not a script held as data: ${it.message}") }
}

private fun outcome(scenarios: List<Scenario>, stopped: ScriptStopped?): JsonObject = buildJsonObject {
    val failed = scenarios.filterNot { it.passed }.map { it.name }
    put("passed", JsonPrimitive(stopped == null && failed.isEmpty()))
    put("failed", JsonPrimitive(failed.joinToString(", ")))
    put("passing", JsonPrimitive(scenarios.size - failed.size))
    put("of", JsonPrimitive(scenarios.size))
    put("scenarios", JsonArray(scenarios.map { scenario ->
        buildJsonObject {
            put("name", JsonPrimitive(scenario.name))
            put("passed", JsonPrimitive(scenario.passed))
            scenario.why?.let { put("why", JsonPrimitive(it)) }
        }
    }))
    stopped?.let { put("stopped", JsonPrimitive(it.message)) }
}

private const val FIRST_OK = 200
private const val LAST_OK = 299
