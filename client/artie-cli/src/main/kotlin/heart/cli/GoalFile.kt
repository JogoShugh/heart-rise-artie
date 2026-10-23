package heart.cli

import heart.script.Goal
import heart.script.Step
import java.nio.file.Files
import java.nio.file.Path

/**
 * A goal file, NAME.goal.md (spec/PLAN-GOAL-MARKDOWN.md): a front matter, then the goal in words or in steps. The
 * YAML goal file it replaced, NAME.goal.yaml (spec/PLAN-PLAIN-TEXT-RUN.md), is told where to go.
 */
fun goalFile(name: String, text: String): Goal =
    if (name.endsWith(".goal.md")) {
        heart.cli.goal.markdownGoal(name, text)
    } else {
        throw BadArguments("a goal file is NAME.goal.md now: a front matter, then the goal in Markdown")
    }

/** Whether [file] is a goal file: in Markdown, or the YAML it replaced, so that one is told where to go. */
internal fun isGoal(file: Path): Boolean = file.fileName.toString().let {
    it.endsWith(".goal.md") || it.endsWith(".goal.yaml")
}

/** The goal [file] holds; a rule it breaks stops the run before its first request, saying so. */
internal fun readGoal(file: Path): Goal = try {
    goalFile(file.fileName.toString(), Files.readString(file))
} catch (e: BadArguments) {
    throw BadArguments("$STOPPED: ${e.message}", e)
}

/**
 * A goal that keeps watching, waits with `Once`, or is written in sections with the manners in words or Done when,
 * isn't run yet (spec/ideas/goals-that-work-alongside-others.md): the first such part of [goal], read from [file],
 * stops the run before its first request, saying so.
 */
internal fun unwatched(goal: Goal, file: Path) {
    fun first(steps: List<Step>): Pair<Int, String>? = steps.firstNotNullOfOrNull { step ->
        when {
            step is Step.Whenever -> step.line to "Whenever"
            step is Step.Until -> step.line to "Until"
            step is Step.Intent && step.once != null -> step.line to "Once"
            step is Step.Intent && step.otherwise != null -> step.line to "Otherwise"
            else -> null
        } ?: first(heart.cli.goal.under(step))
    }
    val parts = listOfNotNull(
        goal.alongside?.let { it.line to "Working alongside others" },
        first(goal.steps),
        goal.done.firstOrNull()?.let { it.line to "Done when" },
    )
    parts.minByOrNull { it.first }?.let { (line, word) ->
        val how = "--plan-only shows how the goal reads"
        throw BadArguments("$STOPPED: ${file.fileName}:$line: $word isn't run yet; $how")
    }
}

/** `--plan-only`: what goal [file] holds, as lines to print; nothing is sent. */
internal fun planned(file: Path): List<String> {
    if (!isGoal(file)) throw BadArguments("--plan-only reads a goal file")
    return heart.cli.goal.planned(readGoal(file))
}
