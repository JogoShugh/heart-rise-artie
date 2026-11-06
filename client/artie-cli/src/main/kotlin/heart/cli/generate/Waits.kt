package heart.cli.generate

/**
 * Rule 7: a wait or a check, copied from the script's own lines when there is the script, else written from what
 * the capture kept of it (the condition waited for; the facts checked).
 */
internal object Waits {

    fun of(step: Step, source: List<String>?): List<String> = copied(step, source) ?: listOf(written(step))

    /** A wait or a check as the script wrote it, every line of it, to its closing bracket. */
    private fun copied(step: Step, source: List<String>?): List<String>? {
        val line = step.line ?: 0
        val first = source?.getOrNull(line - 1) ?: return null
        val indent = first.length - first.trimStart().length
        val taken = mutableListOf<String>()
        var depth = 0
        for (text in source.drop(line - 1)) {
            taken += text.drop(minOf(indent, text.length - text.trimStart().length)).trimEnd()
            depth += text.count { it == '(' || it == '{' } - text.count { it == ')' || it == '}' }
            if (depth <= 0) break
        }
        return taken
    }

    /** A wait or a check as the capture kept it. */
    private fun written(step: Step): String {
        val facts = step.params["facts"].list().map { it.map() }
        return when {
            step.say == "until" -> "until { ${step.waitedFor.orEmpty()} }"
            facts.isEmpty() -> "// review: a check the capture can't write back, at line ${step.line}"
            else -> "check(" + facts.joinToString(", ") { fact(it) } + ")"
        }
    }

    private fun fact(fact: Map<*, *>): String {
        val member = KotlinText.quoted(fact["member"].toString())
        val test = fact["test"].toString()
        val op = test.substringBefore(' ')
        val argument = test.substringAfter(' ', "")
        return when {
            argument.isEmpty() -> "$member to $op"
            argument.matches(SCALAR) -> "$member $op $argument"
            else -> "$member $op ${KotlinText.quoted(argument)}"
        }
    }

    private val SCALAR = Regex("true|false|null|-?[0-9]+(\\.[0-9]+)?")
}
