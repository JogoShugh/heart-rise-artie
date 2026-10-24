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
        goal.manners?.let { "manners $it" },
        goal.inputs.takeIf { it.isNotEmpty() }?.joinToString(prefix = "inputs ") { it.name },
    )
    if (goal.steps.isEmpty()) {
        val sentences = goal.sentences(goal.text)
        return head + sentences.mapIndexed { i, sentence -> "sentence ${i + 1}: $sentence" } +
            "a goal in words: ${sentences.size} sentences, nothing sent"
    }
    val steps = goal.steps.flatMap { lines(it, 0) }
    val alongside = goal.alongside?.words.orEmpty().map { "alongside: $it" }
    val done = goal.done.map { "line ${it.line}: done when: ${it.facts.joinToString("; ")}" }
    return head + listOfNotNull(goal.context.takeIf { it.isNotBlank() }?.let { "context: $it" }) + alongside +
        steps + done + "a goal in steps: ${goal.steps.sumOf(::count)} steps, nothing sent"
}

/** [step] and the steps under it, each a line, its tail lines under it, indented 2 spaces a level of [depth]. */
private fun lines(step: Step, depth: Int): List<String> {
    val pad = "  ".repeat(depth)
    val said = when (step) {
        is Step.Intent -> (step.once?.let { "once: ${it.condition}, then: " }.orEmpty()) +
            (step.condition?.let { "if: $it, then: " }.orEmpty()) + step.words + marks(step)
        is Step.Wait -> "wait: ${step.condition}"
        is Step.Verify -> "verify: ${step.facts.joinToString("; ")}"
        is Step.Whenever -> "whenever: ${step.match}" + step.name?.let { " (as $it)" }.orEmpty()
        is Step.Until -> "until: ${step.condition}"
    }
    val intent = step as? Step.Intent
    // The tail as the file orders it: give-up and otherwise, each at its own line.
    val tail = listOfNotNull(
        intent?.once?.giveUp?.let { it.line to "give up after ${said(it.after)}" },
        intent?.otherwise?.let { it.line to "otherwise: ${it.words}" },
    ).sortedBy { it.first }.map { (line, words) -> "line $line: $pad  $words" }
    return listOf("line ${step.line}: $pad$said") + tail + under(step).flatMap { lines(it, depth + 1) }
}

private fun marks(step: Step.Intent): String = listOfNotNull(
    step.name?.let { " (as $it)" },
    step.moves?.let { " (moves $it)" },
    " (reasoned)".takeIf { step.reasoned },
).joinToString("")

/** How many steps [step] is, with the steps under it. */
private fun count(step: Step): Int = 1 + under(step).sumOf(::count)
