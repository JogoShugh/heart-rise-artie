package heart.cli.goal

import heart.script.Goal
import heart.script.Step

/**
 * What `artie run NAME.goal.md --plan-only` prints (spec/PLAN-GOAL-MARKDOWN.md): where the goal starts, its inputs,
 * then its sentences, or its context and each step with its line, nested as the file nests it. Nothing is sent.
 */
fun planned(goal: Goal): List<String> {
    val head = listOfNotNull(
        "start ${goal.start}",
        goal.inputs.takeIf { it.isNotEmpty() }?.joinToString(prefix = "inputs ") { it.name },
    )
    if (goal.steps.isEmpty()) {
        val sentences = goal.sentences(goal.text)
        return head + sentences.mapIndexed { i, sentence -> "sentence ${i + 1}: $sentence" } +
            "a goal in words: ${sentences.size} sentences, nothing sent"
    }
    val steps = goal.steps.flatMap { lines(it, 0) }
    return head + listOfNotNull(goal.context.takeIf { it.isNotBlank() }?.let { "context: $it" }) + steps +
        "a goal in steps: ${steps.size} steps, nothing sent"
}

/** [step] and the steps under it, a line each, indented 2 spaces a level of [depth]. */
private fun lines(step: Step, depth: Int): List<String> {
    val said = when (step) {
        is Step.Intent -> step.words + listOfNotNull(
            step.name?.let { " (as $it)" },
            step.moves?.let { " (moves $it)" },
            " (reasoned)".takeIf { step.reasoned },
        ).joinToString("")
        is Step.Wait -> "wait: ${step.condition}"
        is Step.Verify -> "verify: ${step.facts.joinToString("; ")}"
    }
    val own = "line ${step.line}: ${"  ".repeat(depth)}$said"
    return listOf(own) + (step as? Step.Intent)?.children.orEmpty().flatMap { lines(it, depth + 1) }
}
