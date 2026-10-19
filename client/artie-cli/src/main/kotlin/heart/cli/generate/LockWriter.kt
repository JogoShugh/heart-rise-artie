package heart.cli.generate

/**
 * Rule 9: what the run was offered, pinned beside the script it gives, never in it: the rung it read at, and by
 * relation each form's own integrity and every affordance set it was offered in, and each link's target.
 */
class LockWriter(capture: Capture) {
    // The rung the run read at: the highest any step was answered at, since a bookmark answers at L0 whatever is asked.
    private val rung = capture.steps.mapNotNull { it.guard["rung"]?.toString() }.maxOrNull()
    private val forms = linkedMapOf<String, Pair<String, LinkedHashSet<String>>>()
    private val links = linkedMapOf<String, String>()

    init {
        capture.steps.filter { it.relation != null }.forEach { step ->
            val relation = step.relation.orEmpty()
            val target = step.chose["target"]?.toString()
            val form = step.guard["form"]?.toString()
            when {
                // A link's target names one resource; what a script relies on is what it leaves to fill.
                step.follows && target != null ->
                    links.putIfAbsent(relation, Regex("\\{[^}]*}").findAll(target).joinToString("") { it.value })
                !step.follows && form != null -> {
                    val sets = forms.getOrPut(relation) { form to linkedSetOf() }.second
                    step.guard["set"].map()["integrity"]?.let { sets += it.toString() }
                }
            }
        }
    }

    val formCount: Int get() = forms.size
    val linkCount: Int get() = links.size

    fun write(script: String, captureName: String): String = buildString {
        appendLine(
            "# What $script was recorded against, from $captureName. " +
                "artie run holds the script to it before every act:",
        )
        appendLine(
            "# --guard lenient (the default) refuses a form no longer offered, or whose integrity changed; " +
                "--guard strict",
        )
        appendLine("# also refuses one offered in a set not recorded for it.")
        rung?.let { appendLine("rung: ${scalar(it)}") }
        appendLine(if (forms.isEmpty()) "forms: {}" else "forms:")
        forms.forEach { (relation, pin) ->
            appendLine("  ${scalar(relation)}:")
            appendLine("    integrity: ${scalar(pin.first)}")
            appendLine(if (pin.second.isEmpty()) "    sets: []" else "    sets:")
            pin.second.forEach { appendLine("      - ${scalar(it)}") }
        }
        appendLine(if (links.isEmpty()) "links: {}" else "links:")
        links.forEach { (relation, target) -> appendLine("  ${scalar(relation)}: ${scalar(target)}") }
    }
}

/** [text] plain where YAML reads it back as the same string, single-quoted where it wouldn't. */
internal fun scalar(text: String): String {
    val plain = text.isNotEmpty() && text.first() !in "{[]},&*#?|-<>=!%@`'\" " && ": " !in text &&
        " #" !in text && !text.endsWith(":")
    return if (plain) text else "'" + text.replace("'", "''") + "'"
}
