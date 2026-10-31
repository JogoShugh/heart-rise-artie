package heart.cli

import heart.script.Goal
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

/** `--plan-only`: what goal [file] holds, as lines to print; nothing is sent. */
internal fun planned(file: Path): List<String> {
    if (!isGoal(file)) throw BadArguments("--plan-only reads a goal file")
    return heart.cli.goal.planned(readGoal(file))
}
