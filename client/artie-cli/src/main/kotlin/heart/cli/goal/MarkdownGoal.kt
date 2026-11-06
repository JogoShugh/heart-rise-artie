package heart.cli.goal

import heart.script.Goal

/**
 * A goal file in Markdown, NAME.goal.md (spec/PLAN-GOAL-MARKDOWN.md): its front matter, then a goal in words or in
 * steps. Every rule it breaks stops the run before its first request, naming the line and what to write instead.
 */
fun markdownGoal(file: String, text: String): Goal {
    val lines = text.lines()
    val front = frontMatter(file, lines)
    val body = body(file, lines, front.end + 1, front.inputs.map { it.name })
    return Goal(
        front.start, front.inputs, body.text, body.context, body.steps, front.limits, front.manners,
        body.alongside, body.done,
    )
}
