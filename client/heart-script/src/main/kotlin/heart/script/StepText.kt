package heart.script

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a planner is shown of a goal in steps (spec/PLAN-GOAL-MARKDOWN.md): each step's words with its names
 * filled, the steps as a tree, the moves a step allows; and the words the run's moves are refused with.
 */
internal object StepText {
    private val NAMED = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)((?:\.[A-Za-z_][A-Za-z0-9_]*)*)}""")

    /**
     * [text] with each `{name}` a step named put as its address, each `{name.member}` as that member of its state,
     * then each input put as its value.
     */
    fun fill(text: String, named: Map<String, Representation>, inputs: Set<String>, value: (String) -> String) =
        GoalText.fill(
            NAMED.replace(text) { slot ->
                val held = named[slot.groupValues[1]] ?: return@replace slot.value
                val member = slot.groupValues[2].removePrefix(".")
                if (member.isEmpty()) held.address else member(held.state, member) ?: slot.value
            },
            inputs,
            value,
        )

    private fun member(state: JsonObject, path: String): String? {
        var at: Any? = state
        path.split('.').forEach { at = (at as? JsonObject)?.get(it) }
        return when (val found = at) {
            null -> null
            is JsonPrimitive -> found.content
            else -> found.toString()
        }
    }

    /** [step] as said: an intent's words; `Wait: …`; `Verify: …`, its facts one after the other. */
    fun said(step: Step): String = when (step) {
        is Step.Intent -> step.words
        is Step.Wait -> "Wait: ${step.condition}"
        is Step.Verify -> "Verify: ${step.facts.joinToString("; ")}"
    }

    /**
     * The steps as far as the planner needs them, a line each, indented as nested: those [done], the steps the one
     * [now] is inside, and the one now, its sub-steps only counted. A planner shown the steps to come does their
     * work early, where they don't start from.
     */
    fun tree(steps: List<Step>, done: Set<Step>, now: Step, depth: Int = 0): List<String> = steps.flatMap { step ->
        val pad = "  ".repeat(depth)
        val children = (step as? Step.Intent)?.children.orEmpty()
        when {
            step === now -> listOf("$pad[now] ${line(step)}") + listOfNotNull(
                children.size.takeIf { it > 0 }?.let {
                    "$pad(" + (if (it == 1) "1 sub-step comes" else "$it sub-steps come") +
                        " next, from where this step ends)"
                },
            )
            inside(step, now) -> listOf("$pad[in] ${line(step)}") + tree(children, done, now, depth + 1)
            step in done -> listOf("$pad[done] ${line(step)}")
            else -> emptyList()
        }
    }

    /** Every step, a line each, indented as nested: the person's words. */
    fun all(steps: List<Step>, depth: Int = 0): List<String> = steps.flatMap { step ->
        listOf("  ".repeat(depth) + line(step)) + all((step as? Step.Intent)?.children.orEmpty(), depth + 1)
    }

    private fun line(step: Step) = said(step) + (step as? Step.Intent)?.name?.let { " (as $it)" }.orEmpty()

    private fun inside(step: Step, now: Step): Boolean =
        (step as? Step.Intent)?.children.orEmpty().any { it === now || inside(it, now) }

    /** The moves [step] allows, by kind. */
    fun allowed(step: Step): Set<String> = when (step) {
        is Step.Intent -> setOf("act", "back", "done", "clarify", "stop")
        is Step.Wait -> setOf("wait", "clarify", "stop")
        is Step.Verify -> setOf("check", "clarify", "stop")
    }

    /** Why [move] is refused at [step], in the words the planner is told. */
    fun refused(move: Move, step: Step): String = "${kind(move)}: refused, " + when {
        move is Move.Back -> "the steps say where to go"
        move is Move.Finish -> "the run ends with the last step"
        step is Step.Intent -> "this step is an intent: act, then done"
        step is Step.Wait -> "this step is a Wait:, which takes a wait"
        else -> "this step is a Verify:, which takes a check"
    }

    private fun kind(move: Move): String = when (move) {
        is Move.Act -> "act"
        is Move.Back -> "back"
        is Move.Wait -> "wait"
        is Move.Check -> "check"
        is Move.Done -> "done"
        is Move.Finish -> "finish"
        is Move.Stop -> "stop"
        is Move.Clarify -> "clarify"
    }
}
